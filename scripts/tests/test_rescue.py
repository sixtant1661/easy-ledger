import importlib.util
from pathlib import Path
import unittest
import tempfile
import tarfile
import io
import json
import sqlite3

spec=importlib.util.spec_from_file_location('rescue',Path(__file__).parents[1]/'rescue-old-ledger.py')
rescue=importlib.util.module_from_spec(spec);spec.loader.exec_module(rescue)
SCHEMA=Path(__file__).parents[2]/'app/schemas/com.easyledger.app.data.local.LedgerDatabase/2.json'

def fixture(folder, price='3.335', amount='6.67', wal=False):
    db=sqlite3.connect(Path(folder)/'easy-ledger.db')
    schema=json.loads(SCHEMA.read_text())['database']
    for entity in schema['entities']:
        db.execute(entity['createSql'].replace('${TABLE_NAME}',entity['tableName']))
        for index in entity.get('indices', []):
            db.execute(index['createSql'].replace('${TABLE_NAME}',entity['tableName']))
    db.execute('PRAGMA user_version=2');db.commit()
    if wal:
        db.execute('PRAGMA journal_mode=WAL');db.execute('PRAGMA wal_autocheckpoint=0')
    db.execute('INSERT INTO projects VALUES (?,?,?,?,?,?,?,?,?)',('p','合成项目','DAY','天',price,1,1,None,'合成数据'))
    db.execute('INSERT INTO work_records VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',('r','p','2026-10-14','DAY','天','2',price,amount,'合成备注',1,1,None,None,'MANUAL',1))
    db.commit()
    return db

def archive(folder):
    output=io.BytesIO()
    with tarfile.open(fileobj=output,mode='w') as tar:
        for file in Path(folder).iterdir():
            if file.name.startswith('easy-ledger.db'):tar.add(file,arcname='databases/'+file.name)
    return output.getvalue()

class RescueTests(unittest.TestCase):
    def test_original_decimal_history_and_wal_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            db=fixture(directory,wal=True)
            try:
                encoded=rescue.convert_archive(archive(directory))
                body=json.loads(json.loads(encoded)['payload'])
                self.assertEqual('6.67',body['activeTotal'])
                self.assertEqual('3.335',body['records'][0]['unitPrice'])
                self.assertEqual(1,len(body['records']))
            finally:db.close()
    def test_wrong_amount_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            db=fixture(directory,amount='6.66');db.close()
            with self.assertRaisesRegex(ValueError,'金额不一致'):rescue.convert_archive(archive(directory))
    def test_archive_traversal_is_rejected(self):
        output=io.BytesIO()
        with tarfile.open(fileobj=output,mode='w') as tar:
            item=tarfile.TarInfo('../../outside');item.size=1;tar.addfile(item,io.BytesIO(b'x'))
        with self.assertRaises(ValueError):rescue.convert_archive(output.getvalue())

if __name__=='__main__':unittest.main()
