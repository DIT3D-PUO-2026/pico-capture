"""Select an explicit release request and extract its authored changelog section."""
import re

VERSION_PATTERN = r'[0-9]+\.[0-9]+(?:\.[0-9]+)?(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?'

def project_version(properties):
    matches = re.findall(r'^appVersionName=(.+)$', properties, re.MULTILINE)
    if len(matches) != 1 or not re.fullmatch(VERSION_PATTERN, matches[0].strip()):
        raise ValueError('gradle.properties must contain one valid appVersionName')
    return matches[0].strip()

def release_requested(event, ref, message, version):
    if event != 'push':
        return False
    if ref.startswith('refs/tags/'):
        if ref != 'refs/tags/v' + version:
            raise ValueError('Release tag must match v' + version)
        return True
    subject = message.splitlines()[0] if message else ''
    if ref != 'refs/heads/main' or re.match(r'^\[release\](?:\s|$)', subject, re.IGNORECASE) is None:
        return False
    requested = re.match(r'^\[release\]\s+(\S+)', subject, re.IGNORECASE)
    if requested is None or requested.group(1).removeprefix('v') != version:
        raise ValueError('Use [release] ' + version + ' to match gradle.properties')
    return True

def changelog_notes(markdown, version):
    lines = markdown.splitlines()
    headings = []
    fence = None
    for index, line in enumerate(lines):
        marker = re.match(r'^\s{0,3}(`{3,}|~{3,})', line)
        if marker:
            kind = marker.group(1)[0]
            if fence is None:
                fence = kind
            elif fence == kind:
                fence = None
            continue
        if fence is None and re.match(r'^##\s+', line):
            headings.append((index,line))
    wanted = re.compile(r'^##\s+(?:\[' + re.escape(version) + r'\]|' + re.escape(version) + r')(?:\s.*)?$')
    matches = [i for i, (_, heading) in enumerate(headings) if wanted.fullmatch(heading)]
    if len(matches) != 1:
        raise ValueError('CHANGELOG.md needs exactly one ## [' + version + '] section')
    pos = matches[0]
    start = headings[pos][0] + 1
    end = headings[pos+1][0] if pos+1 < len(headings) else len(lines)
    body = '\n'.join(lines[start:end]).strip()
    meaningful = re.sub(r'<!--.*?-->', '', body, flags=re.DOTALL).strip()
    if not meaningful or not any(line.strip() and not line.lstrip().startswith('#') for line in meaningful.splitlines()):
        raise ValueError('The release changelog section is empty')
    return body + '\n'
