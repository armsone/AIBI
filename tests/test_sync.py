import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "aibi_sync.py"
SPEC = importlib.util.spec_from_file_location("aibi_sync", MODULE_PATH)
assert SPEC and SPEC.loader
aibi_sync = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(aibi_sync)


class SyncSafetyTests(unittest.TestCase):
    def test_classification(self):
        self.assertEqual(aibi_sync.classify("a", "a", None), "current")
        self.assertEqual(aibi_sync.classify("b", "a", "a"), "update-available")
        self.assertEqual(aibi_sync.classify("a", "b", "a"), "local-modified")
        self.assertEqual(aibi_sync.classify("c", "b", "a"), "diverged-conflict")
        self.assertEqual(aibi_sync.classify("a", None, None), "missing")

    def test_atomic_copy_replaces_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            destination = root / "nested" / "destination"
            source.write_text("new", encoding="utf-8")
            aibi_sync.atomic_copy(source, destination)
            self.assertEqual(destination.read_text(encoding="utf-8"), "new")

    def test_inside_rejects_escape(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(aibi_sync.SyncError):
                aibi_sync.inside(Path(directory), "../outside", "test")

    def test_repository_root_accepts_workspace_sibling(self):
        expected = aibi_sync.PROJECT_ROOT.parent / "StarManager"
        self.assertEqual(aibi_sync.repository_root("../StarManager"), expected.resolve())

    def test_repository_root_rejects_workspace_root(self):
        with self.assertRaises(aibi_sync.SyncError):
            aibi_sync.repository_root("..")

    def test_all_discovers_registered_consumers(self):
        self.assertIn("starmanager", aibi_sync.consumer_names("all"))


if __name__ == "__main__":
    unittest.main()
