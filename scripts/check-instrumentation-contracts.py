#!/usr/bin/env python3
"""Check JUnit4 lifecycle/test JVM signatures before starting a costly emulator."""
import os
from pathlib import Path
import re
import subprocess

root=Path('app/build/tmp/kotlin-classes/debugAndroidTest')
classes=[str(p.relative_to(root).with_suffix('')).replace('/','.') for p in root.rglob('*Test.class') if '$' not in p.name]
if not classes:raise SystemExit('No compiled instrumentation test classes; run assembleDebugAndroidTest first')
java_home=os.environ.get('JAVA_HOME')
javap=str(Path(java_home)/'bin/javap') if java_home else 'javap'
result=subprocess.run([javap,'-v','-classpath',str(root),*classes],capture_output=True,text=True,check=True)
method='';descriptor='';checked=0
for line in result.stdout.splitlines():
    if re.match(r'^  (public|protected|private).*\);$',line):method=line.strip();descriptor=''
    if line.strip().startswith('descriptor:'):descriptor=line.split(':',1)[1].strip()
    if line.strip() in ('org.junit.Test','org.junit.Before','org.junit.After','org.junit.BeforeClass','org.junit.AfterClass'):
        checked+=1
        if descriptor!='()V':raise SystemExit('JUnit4 method must have no arguments and return void: '+method)
if not checked:raise SystemExit('No JUnit4 methods checked')
print('JUnit4 JVM contracts verified:',checked,'methods')
