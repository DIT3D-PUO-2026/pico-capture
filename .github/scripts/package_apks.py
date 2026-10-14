"""Package Gradle APK outputs without including signing keys or local settings."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil

root = Path(__file__).resolve().parents[2]
out = root / 'dist'

def apk(variant):
    folder = root / 'app/build/outputs/apk' / variant
    data = json.loads((folder / 'output-metadata.json').read_text(encoding='utf-8'))
    if data['applicationId'] != 'net.local.picocapture' or len(data['elements']) != 1:
        raise SystemExit('Unexpected APK metadata')
    item = data['elements'][0]
    path = (folder / item['outputFile']).resolve()
    if path.parent != folder.resolve() or not path.is_file():
        raise SystemExit('Missing or unexpected APK output path')
    return path, item['versionName'], item['versionCode']

debug, version, code = apk('debug')
release, release_version, release_code = apk('release')
if (release_version, release_code) != (version, code):
    raise SystemExit('Debug and release versions do not match')
if not re.fullmatch(r'[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?', version):
    raise SystemExit('Invalid app version')
signed = not release.name.endswith('-unsigned.apk')
tag = os.environ.get('GITHUB_REF', '')
if tag.startswith('refs/tags/') and (tag != 'refs/tags/v' + version or not signed):
    raise SystemExit('Release tag must match v' + version + ' and use the project signing key')

out.mkdir(exist_ok=True)
# Only these named outputs are uploaded by the workflow.
for stale in out.glob('*'):
    if stale.is_file():
        stale.unlink()
shutil.copy2(debug, out / ('PicoCapture-' + version + '-debug.apk'))
suffix = '.apk' if signed else '-unsigned.apk'
shutil.copy2(release, out / ('PicoCapture-' + version + suffix))
notices = [
    ('Pico Capture - MIT', root / 'LICENSE'),
    ('Pico3dResolution UI reference - MIT', root / 'third_party/licenses/Pico3dResolution-MIT.txt'),
    ('Qualcomm SGSR 1 - BSD-3-Clause', root / 'app/src/main/assets/SGSR-LICENSE.txt'),
]
(out / 'LICENSES.txt').write_text('\n\n'.join(title + '\n\n' + path.read_text(encoding='utf-8') for title, path in notices) + '\n', encoding='utf-8')
info = {'versionName': version, 'versionCode': code, 'commit': os.environ.get('GITHUB_SHA', 'local'),
        'releaseSigned': signed, 'debugSigning': 'project-key' if signed else 'temporary-local-debug-key'}
(out / 'build-info.json').write_text(json.dumps(info, indent=2) + '\n', encoding='utf-8')
notes = root / 'build/release-notes.md'
if notes.is_file():
    shutil.copy2(notes, out / 'RELEASE_NOTES.md')
files = sorted(p for p in out.iterdir() if p.is_file())
(out / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in files), encoding='utf-8')
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as stream:
        stream.write('version=' + version + '\nsigned=' + str(signed).lower() + '\n')
print(json.dumps(info))
