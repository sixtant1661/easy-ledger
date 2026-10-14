#!/usr/bin/env python3
"""Run Gradle in the prepared cloud snapshot; never print proxy credentials."""
import os
import re
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse

root = Path(__file__).resolve().parent.parent
tooling = Path('/workspace/easy-ledger-tooling')
jdk = tooling / 'jdk-17.0.18+8'
sdk = Path('/workspace/android-sdk')
if not (jdk / 'bin/javac').is_file() or not (sdk / 'platforms/android-35/android.jar').is_file():
    sys.exit('Cloud JDK/SDK missing. Follow README local setup or the environment install script.')
environment = os.environ.copy()
environment.update(JAVA_HOME=str(jdk), ANDROID_HOME=str(sdk),
                   ANDROID_USER_HOME=str(tooling / 'android-user'),
                   GRADLE_USER_HOME='/workspace/.gradle-easy-ledger')
Path(environment['ANDROID_USER_HOME']).mkdir(parents=True, exist_ok=True)
options = []
truststore = tooling / 'cacerts'
if truststore.is_file():
    options += [f'-Djavax.net.ssl.trustStore={truststore}', '-Djavax.net.ssl.trustStorePassword=changeit']
proxy = urlparse(environment.get('HTTPS_PROXY', ''))
if proxy.username or proxy.password:
    sys.exit('Authenticated proxy requires supported platform configuration; credentials will not be extracted.')
if proxy.hostname and not re.fullmatch(r"[A-Za-z0-9.-]+", proxy.hostname):
    sys.exit("Unsupported proxy hostname")
if proxy.hostname:
    for protocol in ('http', 'https'):
        options += [f'-D{protocol}.proxyHost={proxy.hostname}',
                    f'-D{protocol}.proxyPort={proxy.port or 80}',
                    f'-D{protocol}.nonProxyHosts=localhost|127.*']
# Wrapper bootstrap also needs proxy/trust settings before Gradle itself starts.
environment["JAVA_OPTS"] = " ".join(options)
# Pass Gradle JVM arguments separately, without shell interpolation of proxy metadata.
args = sys.argv[1:] or ['testDebugUnitTest', 'lintDebug', 'assembleDebug', 'assembleDebugAndroidTest']
result = subprocess.run([str(root / 'gradlew'), '--no-daemon', *options, *args, '--console=plain'],
                        cwd=root, env=environment)
sys.exit(result.returncode)
