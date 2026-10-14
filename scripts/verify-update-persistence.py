#!/usr/bin/env python3
"""Emulator-only check: seed data, force-stop, reinstall same signed APK, verify."""
import subprocess

APP = 'app/build/outputs/apk/debug/app-debug.apk'
TEST = 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
CLASS = 'com.easyledger.app.UpdatePersistenceTest'
RUNNER = 'com.easyledger.app.test/androidx.test.runner.AndroidJUnitRunner'


def command(*args):
    return subprocess.run(args, text=True, capture_output=True, check=True)


def phase(name):
    result = command('adb', 'shell', 'am', 'instrument', '-w', '-e', 'class', CLASS,
                     '-e', 'phase', name, RUNNER)
    print(result.stdout)
    if 'OK (1 test)' not in result.stdout or 'FAILURES' in result.stdout:
        print('::error title=APK update persistence::' + name + ' phase failed')
        raise SystemExit(1)


command('adb', 'install', '-r', APP)
command('adb', 'install', '-r', TEST)
phase('seed')
command('adb', 'shell', 'am', 'force-stop', 'com.easyledger.app')
command('adb', 'install', '-r', APP)
phase('verify')
print('Signed APK replacement retained saved record and onboarding preference.')
