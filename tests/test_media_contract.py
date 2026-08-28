import json
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class MediaContractTests(unittest.TestCase):
    def test_active_providers_support_eight_ordered_images(self):
        registry = json.loads((ROOT / "packages/providers/aibi-providers.json").read_text())
        for provider_id in ("gemini", "chatgpt", "claude"):
            provider = registry["providers"][provider_id]
            capability = provider["mediaCapabilities"]
            selectors = provider["selectors"]
            self.assertTrue(capability["supportsImages"])
            self.assertEqual(capability["maxImagesPerTask"], 8)
            self.assertTrue(selectors["attachmentInput"])
            self.assertTrue(selectors["attachmentTrigger"])
            self.assertTrue(selectors["attachmentPreview"])

    def test_provider_fixtures_are_sanitized_and_atomic(self):
        for path in sorted((ROOT / "fixtures/providers").glob("*-multi-image.json")):
            fixture = json.loads(path.read_text())
            self.assertTrue(fixture["sanitized"])
            self.assertEqual(fixture["requestedImageCount"], 8)
            self.assertEqual(fixture["expected"], "all-eight-previews-before-submit")

    def test_runtime_assigns_all_images_and_rejects_non_multiple_input(self):
        subprocess.run(
            ["node", str(ROOT / "tests/runtime_media_test.js")],
            cwd=ROOT,
            check=True,
        )

    def test_platform_pipelines_share_limits_and_ordered_names(self):
        android = (ROOT / "packages/android/AIBIMediaPipeline.kt").read_text()
        apple = (ROOT / "packages/apple/AIBIMediaPipeline.swift").read_text()
        for source in (android, apple):
            self.assertIn("maximumImageCount", source)
            self.assertIn("2_000_000", source)
            self.assertIn("2_048", source)
            self.assertIn("aibi-", source)


if __name__ == "__main__":
    unittest.main()
