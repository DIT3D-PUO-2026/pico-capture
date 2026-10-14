"""Release routing and note-selection checks; no network or publishing."""
import unittest
from unittest.mock import patch
from release_notes import changelog_notes, project_version, release_requested
from publish_release import verify_tag

class ReleaseChecks(unittest.TestCase):
    def test_main_build_does_not_release(self):
        self.assertFalse(release_requested('push','refs/heads/main','Fix OBS buffering','0.1-beta'))

    def test_explicit_main_release(self):
        self.assertTrue(release_requested('push','refs/heads/main','[release] 0.1-beta','0.1-beta'))

    def test_wrong_commit_version_is_rejected(self):
        with self.assertRaises(ValueError): release_requested('push','refs/heads/main','[release] 0.2-beta','0.1-beta')

    def test_release_marker_requires_version(self):
        with self.assertRaises(ValueError): release_requested('push','refs/heads/main','[release]','0.1-beta')

    def test_pull_request_cannot_release(self):
        self.assertFalse(release_requested('pull_request','refs/heads/main','[release] 0.1-beta','0.1-beta'))

    def test_feature_branch_cannot_release(self):
        self.assertFalse(release_requested('push','refs/heads/feature','[release] 0.1-beta','0.1-beta'))

    def test_marker_in_body_does_not_release(self):
        self.assertFalse(release_requested('push','refs/heads/main','Document release process\n\n[release]','0.1-beta'))

    def test_matching_tag(self):
        self.assertTrue(release_requested('push','refs/tags/v0.1-beta','','0.1-beta'))

    def test_wrong_tag_is_rejected(self):
        with self.assertRaises(ValueError): release_requested('push','refs/tags/v0.2-beta','','0.1-beta')

    def test_extract_only_requested_version(self):
        text = '## [Unreleased]\n- Future work\n## [0.1-beta] - 2026-10-14\n### Added\n- Recording\n## [0.0]\n- Older work\n'
        self.assertEqual(changelog_notes(text,'0.1-beta'),'### Added\n- Recording\n')

    def test_duplicate_and_empty_entries_are_rejected(self):
        for text in ('## [0.1-beta]\n## [Other]','## [0.1-beta]\n<!-- placeholder -->','## [0.1-beta]\n- One\n## [0.1-beta]\n- Two'):
            with self.subTest(text=text), self.assertRaises(ValueError): changelog_notes(text,'0.1-beta')

    def test_code_fences_do_not_end_section(self):
        self.assertIn('## example',changelog_notes('## [0.1-beta]\n- Example\n```md\n## example\n```\n## [Other]\n- Later','0.1-beta'))

    def test_invalid_or_duplicate_version_is_rejected(self):
        for text in ('appVersionName=bad/version','appVersionName=0.1-beta\nappVersionName=0.2-beta'):
            with self.subTest(text=text), self.assertRaises(ValueError): project_version(text)

    def test_different_existing_tag_is_rejected(self):
        with patch('publish_release.gh_api',return_value={'object':{'type':'commit','sha':'b'*40}}):
            with self.assertRaises(SystemExit): verify_tag('org/repo','v0.1-beta','a'*40)

    def test_annotated_tag_target(self):
        with patch('publish_release.gh_api',side_effect=[{'object':{'type':'tag','sha':'b'*40}},{'object':{'type':'commit','sha':'a'*40}}]):
            verify_tag('org/repo','v0.1-beta','a'*40)

if __name__ == '__main__':
    unittest.main()
