"""Plan a release from a pushed version tag or an explicit [release] commit."""
from pathlib import Path
import os
import subprocess
from release_notes import project_version, release_requested, changelog_notes

root = Path(__file__).resolve().parents[2]
version = project_version((root / 'gradle.properties').read_text(encoding='utf-8'))
message = subprocess.check_output(['git', '-C', str(root), 'log', '-1', '--format=%B'], text=True, encoding='utf-8')
try:
    publish = release_requested(os.environ.get('GITHUB_EVENT_NAME', ''), os.environ.get('GITHUB_REF', ''), message, version)
    try:
        notes = changelog_notes((root / 'CHANGELOG.md').read_text(encoding='utf-8'), version)
    except (FileNotFoundError, ValueError):
        if publish:
            raise
        notes = None
except (FileNotFoundError, ValueError) as error:
    raise SystemExit(str(error))
notes_file = root / 'build/release-notes.md'
notes_file.parent.mkdir(exist_ok=True)
if notes is not None:
    notes_file.write_text(notes, encoding='utf-8')
elif notes_file.exists():
    notes_file.unlink()
if os.environ.get('GITHUB_OUTPUT'):
    with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
        output.write('publish=' + str(publish).lower() + '\nversion=' + version + '\ntag=v' + version + '\n')
print('Release requested: ' + str(publish).lower() + '; version: ' + version)
