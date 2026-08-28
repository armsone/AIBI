import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class OptInTwentyImageLimitTests(unittest.TestCase):
    def test_portable_engines_accept_opt_in_twenty_image_tasks(self):
        apple = (ROOT / "packages/apple/AIBIEngine.swift").read_text()
        android = (ROOT / "packages/android/AIBIEngine.kt").read_text()

        self.assertIn("task.attachments.count > 20", apple)
        self.assertNotIn("task.attachments.count > 8", apple)
        self.assertIn("task.attachments.size > 20", android)
        self.assertNotIn("task.attachments.size > 8", android)


if __name__ == "__main__":
    unittest.main()
