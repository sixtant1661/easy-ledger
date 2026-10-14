#!/usr/bin/env python3
"""Read-only extraction from the installed DEBUG app, no root/reset/uninstall.
Stop on run-as denial. Never attempt root, permissions changes, or adb backup.
Local output contains private records: keep outside Git and copy to a safe disk.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tarfile
import tempfile
from decimal import Decimal, ROUND_HALF_UP, localcontext
from datetime import date

PACKAGE = 'com.easyledger.app'
DB = 'easy-ledger.db'
LIMIT = 32 * 1024 * 1024

def convert_archive(raw):
    if len(raw) > LIMIT:
        raise ValueError('数据库归档超过32MB；原 APP 未修改，请联系开发者')
    with tempfile.TemporaryDirectory() as folder:
        target = Path(folder)
        with tarfile.open(fileobj=io.BytesIO(raw), mode='r:') as archive:
            allowed = {'databases/' + DB, 'databases/' + DB + '-wal', 'databases/' + DB + '-shm'}
            for member in archive.getmembers():
                if member.name not in allowed or not member.isfile() or member.size > LIMIT:
                    raise ValueError('归档包含不支持的路径或文件，已停止')
                (target / Path(member.name).name).write_bytes(archive.extractfile(member).read())
        if not (target / DB).is_file():
            raise ValueError('旧数据库缺失，已停止')
        # Operate only on temporary copies; SQLite replays WAL without touching the phone.
        with sqlite3.connect(target / DB) as db:
            db.row_factory = sqlite3.Row
            if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok' or db.execute('PRAGMA foreign_key_check').fetchall():
                raise ValueError('数据库完整性检查失败；原 APP 未修改')
            if db.execute('PRAGMA user_version').fetchone()[0] != 2:
                raise ValueError('旧数据库版本不是2，停止自动转换')
            def rows(table):
                return [dict(row) for row in db.execute('SELECT * FROM ' + table + ' ORDER BY ' + ('key' if table == 'preferences' else 'id'))]
            data = {'schemaVersion': 2, 'projects': rows('projects'), 'records': rows('work_records'),
                    'segments': rows('work_segments'), 'preferences': rows('preferences')}
        with localcontext() as ctx:
            ctx.prec = 160
            total = Decimal('0')
            for row in data['records']:
                date.fromisoformat(row['date'])
                qty, price, amount = [Decimal(row[key]) for key in ('quantity', 'unitPrice', 'amount')]
                if not all(v.is_finite() for v in (qty, price, amount)) or qty <= 0 or price < 0 or row['calculationPolicyVersion'] != 1:
                    raise ValueError('记录包含不支持的计费数据')
                expected = (qty * price).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
                if expected != amount:
                    raise ValueError('记录金额不一致，已停止转换；原数据保留')
                if row['deletedAt'] is None:
                    total += amount
            data['activeTotal'] = format(total.quantize(Decimal('.01'), rounding=ROUND_HALF_UP), '.2f')
        payload = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
        result = json.dumps({'format': 'easy-ledger-backup', 'version': 1, 'payload': payload,
                             'sha256': hashlib.sha256(payload.encode()).hexdigest()}, ensure_ascii=False)
        if len(result.encode()) > LIMIT:
            raise ValueError('转换文件超过32MB；未修改手机数据')
        return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial')
    parser.add_argument('--output', required=True, help='New folder OUTSIDE any Git checkout')
    args = parser.parse_args()
    folder = Path(args.output).expanduser().resolve()
    if folder.exists():
        raise ValueError('输出目录已存在，避免覆盖备份，请换一个新目录')
    if any((parent / '.git').exists() for parent in [folder, *folder.parents]):
        raise ValueError('不能把手机真实数据写入 Git 仓库')
    adb = [args.adb] + (['-s', args.serial] if args.serial else [])
    def call(*parts):
        return subprocess.run(adb + list(parts), check=True, capture_output=True, timeout=60).stdout
    # Explicit serial is required if multiple devices; adb itself safely rejects ambiguity.
    call('get-state')
    if call('shell', 'run-as', PACKAGE, 'id').strip() == b'':
        raise ValueError('旧版 run-as 不可用，停止；不要 Root、卸载或清除数据')
    call('shell', 'am', 'force-stop', PACKAGE)
    # Stopping the process fixes the DB+WAL snapshot; this does not clear records.
    raw = call('exec-out', "run-as " + PACKAGE + " sh -c 'cd databases && tar -cf - easy-ledger.db $(test ! -f easy-ledger.db-wal || echo easy-ledger.db-wal) $(test ! -f easy-ledger.db-shm || echo easy-ledger.db-shm)'")
    # Normalize a flat tar without extracting any untrusted filesystem paths.
    normalized = io.BytesIO()
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:') as source, tarfile.open(fileobj=normalized, mode='w') as dest:
        for item in source.getmembers():
            if item.name not in (DB, DB + '-wal', DB + '-shm') or not item.isfile():
                raise ValueError('归档路径异常，停止')
            item.name = 'databases/' + item.name
            dest.addfile(item, source.extractfile(item))
    raw = normalized.getvalue()
    backup = convert_archive(raw)
    folder.mkdir(parents=True, mode=0o700)
    (folder / 'old-ledger-private.tar').write_bytes(raw)
    (folder / 'easy-ledger-backup.json').write_text(backup, encoding='utf-8')
    (folder / 'checksums.json').write_text(json.dumps({'archiveSha256': hashlib.sha256(raw).hexdigest(),
        'backupSha256': hashlib.sha256(backup.encode()).hexdigest()}, indent=2))
    for path in folder.iterdir():
        try: path.chmod(0o600)
        except OSError: pass
    print('已安全取出并校验备份。旧 APP 和记录未删除。请保留原始 tar 和 JSON 各两份。')
    print('文件保存在你指定的新目录中；不要公开上传或提交 Git。')

if __name__ == '__main__':
    try: main()
    except (ValueError, subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError, sqlite3.Error, tarfile.TarError) as exc:
        # Do not print database rows, paths from phone data, or raw adb output.
        if isinstance(exc, (subprocess.CalledProcessError,subprocess.TimeoutExpired)):
            print('USB/授权/run-as 读取失败，已停止。未卸载、未清除或改写手机数据库。')
        else: print(str(exc))
        raise SystemExit(1)
