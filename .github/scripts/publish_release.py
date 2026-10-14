"""Publish authored notes and built assets, protecting existing tag targets."""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess

def gh_api(path):
    result = subprocess.run(['gh', 'api', path], capture_output=True, text=True)
    if result.returncode:
        if 'HTTP 404' in result.stderr:
            return None
        raise SystemExit('GitHub API lookup failed; check repository permissions')
    return json.loads(result.stdout)

def verify_tag(repo, tag, expected_commit):
    ref = gh_api('repos/' + repo + '/git/ref/tags/' + tag)
    if ref is None:
        return  # gh release create will create the tag at --target.
    obj = ref['object']
    for _ in range(8):
        if obj['type'] != 'tag':
            break
        tag_object = gh_api('repos/' + repo + '/git/tags/' + obj['sha'])
        if tag_object is None:
            raise SystemExit('Cannot resolve annotated release tag')
        obj = tag_object['object']
    if obj['type'] != 'commit' or obj['sha'] != expected_commit:
        raise SystemExit('Existing release tag points to a different commit; use a new version')

def public_release_assets(out, version):
    if not re.fullmatch(r'[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?', version):
        raise SystemExit('Invalid public release version')
    selected = [out / ('PicoCapture-' + version + '.apk'), out / 'LICENSES.txt']
    for path in selected:
        if not path.is_file() or path.resolve().parent != out.resolve():
            raise SystemExit('Required public release asset is missing or outside the build directory')
    # The Actions bundle keeps its complete checksums. Public checksums cover only public files.
    public = out / 'public'
    public.mkdir(exist_ok=True)
    checksums = public / 'SHA256SUMS.txt'
    checksums.write_text(''.join(hashlib.sha256(path.read_bytes()).hexdigest() + '  ' + path.name + '\n' for path in selected), encoding='utf-8')
    return selected + [checksums]

def main():
    root = Path(__file__).resolve().parents[2]
    out = root / 'dist'
    info = json.loads((out / 'build-info.json').read_text(encoding='utf-8'))
    version = os.environ['APP_VERSION']
    tag = os.environ['RELEASE_TAG']
    commit = os.environ['RELEASE_COMMIT']
    repo = os.environ['GH_REPO']
    if not re.fullmatch(r'[0-9a-f]{40}', commit):
        raise SystemExit('Expected a full release commit SHA')
    if tag != 'v' + version or info['versionName'] != version or info['commit'] != commit or info['releaseSigned'] is not True:
        raise SystemExit('Release metadata does not match the verified signed build')
    notes = out / 'RELEASE_NOTES.md'
    if not notes.is_file() or not notes.read_text(encoding='utf-8').strip():
        raise SystemExit('Release notes are missing')
    verify_tag(repo, tag, commit)
    assets = [str(path) for path in public_release_assets(out, version)]
    subprocess.run(['gh', 'release', 'create', tag, *assets, '--target', commit,
                    '--prerelease', '--title', 'Pico Capture ' + version,
                    '--notes-file', str(notes)], check=True, cwd=root)

if __name__ == '__main__':
    main()
