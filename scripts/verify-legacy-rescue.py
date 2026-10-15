#!/usr/bin/env python3
"""EMULATOR ONLY. Exercise the real v0.2 package's run-as, WAL snapshot and JSON import.
Never run this fixture setup on a user's physical phone.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import sys
sys.path.insert(0,str(Path(__file__).resolve().parent/'tests'))
from test_rescue import fixture

OLD='com.easyledger.app';NEW='com.easyledger.app.recovery'
RUNNER=NEW+'.test/androidx.test.runner.AndroidJUnitRunner'
def adb(*args,data=None):
    return subprocess.run(['adb',*args],input=data,capture_output=True,check=True,timeout=120).stdout
if adb('shell','getprop','ro.kernel.qemu').strip()!=b'1':raise SystemExit('Refusing: this test requires a synthetic Android emulator, not a phone.')
# connectedDebugAndroidTest normally uninstalls its synthetic app/test packages after completion.
# Reinstall BOTH new-package APKs here; this script is guarded to an emulator before any installs.
adb('install','-r','app/build/outputs/apk/debug/app-debug.apk')
adb('install','-r','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
legacy=Path('legacy-source/app/build/outputs/apk/debug/app-debug.apk')
if not legacy.is_file():raise SystemExit('Legacy source APK missing')
adb('install','-r',str(legacy))
adb('shell','am','start','-W','-n',OLD+'/com.easyledger.app.MainActivity')
adb('shell','am','force-stop',OLD)
adb('shell','run-as',OLD,'mkdir','-p','databases')
with tempfile.TemporaryDirectory() as directory:
    source=Path(directory)
    # CI emulator is freshly created, and the legacy app has only an empty new database.
    # Keep a live WAL containing synthetic records, just as an abruptly stopped app can.
    db=fixture(source,wal=True)
    try:
        for file in source.iterdir():
            if file.name.startswith('easy-ledger.db'):
                adb('exec-in',f"run-as {OLD} sh -c 'cat > databases/{file.name}'",data=file.read_bytes())
    finally:db.close()
    names=['easy-ledger.db','easy-ledger.db-wal','easy-ledger.db-shm']
    def original_hashes():
        return {name:hashlib.sha256(adb('exec-out','run-as',OLD,'cat','databases/'+name)).hexdigest() for name in names}
    original=original_hashes()
    output=source/'rescued'
    subprocess.run([sys.executable,'scripts/rescue-old-ledger.py','--output',str(output)],check=True)
    restored=json.loads(json.loads((output/'easy-ledger-backup.json').read_text())['payload'])
    assert restored['activeTotal']=='6.67' and restored['records'][0]['unitPrice']=='3.335'
    adb('shell','run-as',NEW,'mkdir','-p','files')
    adb('exec-in',f"run-as {NEW} sh -c 'cat > files/legacy-migration.json'",data=(output/'easy-ledger-backup.json').read_bytes())
    result=adb('shell','am','instrument','-w','-e','class','com.easyledger.app.SafetyMigrationTest',RUNNER).decode()
    print(result)
    if 'OK (1 test)' not in result or 'FAILURES' in result:raise SystemExit('Legacy import verification failed')
    after=original_hashes()
    assert original==after,'Legacy DB/WAL/SHM was modified during recovery'
    adb('shell','pm','path',OLD)
print('::notice title=Legacy non-destructive rescue::Actual v0.2 debug package run-as allowed; DB+WAL extracted; historical price and amount imported; original app remained installed and DB/WAL/SHM unchanged. Emulator only, not Samsung hardware.')
