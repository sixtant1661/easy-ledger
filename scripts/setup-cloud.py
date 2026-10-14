#!/usr/bin/env python3
"""Prepare pinned Linux cloud tools outside the checkout; retain verification."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
from urllib.parse import urlparse
import zipfile

TOOLING = Path('/workspace/easy-ledger-tooling')
SDK = Path('/workspace/android-sdk')
JDK = TOOLING / 'jdk-17.0.18+8'
TOOLS = SDK / 'cmdline-tools/19.0'
TOOLING.mkdir(parents=True, exist_ok=True)


def download(url, target, algorithm, checksum):
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2',
                    url, '--output', str(target)], check=True)
    actual = hashlib.new(algorithm, target.read_bytes()).hexdigest()
    if actual != checksum:
        target.unlink()
        raise RuntimeError('Official artifact checksum mismatch')


with tempfile.TemporaryDirectory(prefix='easy-ledger-') as temporary:
    temporary = Path(temporary)
    if not (JDK / 'bin/javac').exists():
        archive = temporary / 'jdk.tar.gz'
        download('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.18%2B8/'
                 'OpenJDK17U-jdk_x64_linux_hotspot_17.0.18_8.tar.gz', archive, 'sha256',
                 '0c94cbb54325c40dcf026143eb621562017db5525727f2d9131a11250f72c450')
        with tarfile.open(archive) as source:
            source.extractall(TOOLING, filter='data')
    if not (TOOLS / 'bin/sdkmanager').exists():
        archive = temporary / 'cmdline.zip'
        # Checksum published in Google's SDK repository manifest for command-line tools 19.0.
        download('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip',
                 archive, 'sha1', '5fdcc763663eefb86a5b8879697aa6088b041e70')
        with zipfile.ZipFile(archive) as source:
            for entry in source.infolist():
                relative = Path(entry.filename).relative_to('cmdline-tools')
                if '..' in relative.parts:
                    raise RuntimeError('Unsafe SDK archive path')
                target = TOOLS / relative
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(source.read(entry))
                    if entry.external_attr >> 16:
                        target.chmod(entry.external_attr >> 16)

# Refresh platform trust separately; never disable TLS checks or save proxy secrets.
truststore = TOOLING / 'cacerts'
shutil.copyfile(JDK / 'lib/security/cacerts', truststore)
certificate = Path('/usr/local/share/ca-certificates/environment-proxy-ca.crt')
if certificate.exists():
    subprocess.run([str(JDK / 'bin/keytool'), '-importcert', '-noprompt',
                    '-alias', 'easy-ledger-environment-proxy', '-file', str(certificate),
                    '-keystore', str(truststore), '-storepass', 'changeit'], check=True)
user_directory = TOOLING / 'android-user'
user_directory.mkdir(parents=True, exist_ok=True)
environment = os.environ.copy()
environment.update(JAVA_HOME=str(JDK), ANDROID_HOME=str(SDK), ANDROID_USER_HOME=str(user_directory))
environment['JAVA_OPTS'] = (f'-Djavax.net.ssl.trustStore={truststore} '
                            '-Djavax.net.ssl.trustStorePassword=changeit')
proxy = urlparse(environment.get('HTTPS_PROXY', ''))
if proxy.username or proxy.password:
    raise RuntimeError('Use supported platform proxy bindings; do not extract credentials')
proxy_args = (['--proxy=http', '--proxy_host=' + proxy.hostname,
               '--proxy_port=' + str(proxy.port or 80)] if proxy.hostname else [])
command = [str(TOOLS / 'bin/sdkmanager'), '--sdk_root=' + str(SDK), *proxy_args]
required = [SDK / 'platforms/android-35/android.jar', SDK / 'build-tools/35.0.0/aapt2',
            SDK / 'platform-tools/adb']
if not all(path.is_file() for path in required):
    subprocess.run(command + ['--licenses'], input='y\n' * 100, text=True, env=environment, check=True)
    subprocess.run(command + ['platforms;android-35', 'build-tools;35.0.0', 'platform-tools'],
                   env=environment, check=True)
if not all(path.is_file() for path in required):
    raise RuntimeError('SDK installation is incomplete')
subprocess.run([str(JDK / 'bin/javac'), '-version'], check=True)
print('Cloud JDK 17 and Android SDK 35 prepared; use scripts/cloud-build.py for Gradle.')
