#!/usr/bin/env python3
"""Initialize a PRIVATE new-package key, save it outside Git, configure repository secrets.
Never replace the legacy certificate or claim the new key can update the old package.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import subprocess

parser=argparse.ArgumentParser();parser.add_argument("--prepare-only",action="store_true");args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
private=Path('/workspace/easy-ledger-private/signing')
private.mkdir(parents=True,exist_ok=True,mode=0o700)
private.chmod(0o700)
key=private/'easy-ledger-recovery.jks';credentials=private/'credentials.json'
keytool='/workspace/easy-ledger-tooling/jdk-17.0.18+8/bin/keytool'
if key.exists()!=credentials.exists():raise SystemExit('Signing files incomplete; stop without replacing the existing key.')
if not key.exists():
    password=secrets.token_urlsafe(32)
    env=os.environ.copy();env['LEDGER_KEY_PASSWORD']=password
    subprocess.run([keytool,'-genkeypair','-alias','easy-ledger','-keyalg','RSA','-keysize','3072','-validity','10950',
        '-keystore',str(key),'-storetype','PKCS12','-storepass:env','LEDGER_KEY_PASSWORD','-keypass:env','LEDGER_KEY_PASSWORD',
        '-dname','CN=Easy Ledger, OU=Offline Android, O=sixtant1661'],env=env,check=True,capture_output=True)
    credentials.write_text(json.dumps({'password':password}));key.chmod(0o600);credentials.chmod(0o600)
password=json.loads(credentials.read_text())['password'];env=os.environ.copy();env['LEDGER_KEY_PASSWORD']=password
result=subprocess.run([keytool,'-list','-v','-alias','easy-ledger','-keystore',str(key),'-storepass:env','LEDGER_KEY_PASSWORD'],env=env,check=True,capture_output=True,text=True)
match=re.search(r'SHA256:\s*([0-9A-F:]+)',result.stdout)
if not match:raise SystemExit('Certificate could not be verified')
fingerprint=match.group(1).replace(':','').lower()
pin=root/'.github/recovery-signing-cert.sha256'
if pin.exists() and pin.read_text().strip()!=fingerprint:raise SystemExit('New-package key differs from its pinned certificate: stop')
pin.write_text(fingerprint+'\n')
(private/'github-key.txt').write_text(base64.b64encode(key.read_bytes()).decode())
(private/'github-password.txt').write_text(password)
for file in private.iterdir(): file.chmod(0o600)
if args.prepare_only:
    print('Private signing inputs prepared; no repository secret was written. Public fingerprint pinned.')
    raise SystemExit(0)
for name,value in [('EASY_LEDGER_SIGNING_KEY_BASE64',base64.b64encode(key.read_bytes())),('EASY_LEDGER_SIGNING_PASSWORD',password.encode())]:
    result=subprocess.run(['gh','secret','set',name,'--repo','sixtant1661/easy-ledger'],input=value,capture_output=True)
    if result.returncode:raise SystemExit('Repository secret configuration failed: '+name+'; no secret value printed. Existing app untouched.')
    print('Repository secret configured:',name)
pin.write_text(fingerprint+'\n')
print('New-package key saved privately and certificate pinned. This key cannot replace the legacy app.')
