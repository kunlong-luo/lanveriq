import unittest
from gpui_default_icon_attribution import classify, git_blob

class DefaultIconReview(unittest.TestCase):
    def test_exact_or_open(self):
        body=b"<svg/>"
        blob=git_blob(body)
        self.assertEqual(classify(body,{"gpui_blob":blob,"lucide_blob":blob}),
                         "LUCIDE_EXACT_SAME_PATH_NOTICE_CANDIDATE")
        for upstream in (None,"0"*40):
            self.assertEqual(classify(body,{"gpui_blob":blob,"lucide_blob":upstream}),
                             "RETAINED_OR_CHANGED_ICON_ATTRIBUTION_OPEN")
    def test_drift_refused(self):
        with self.assertRaises(ValueError):
            classify(b"changed",{"gpui_blob":git_blob(b"original"),"lucide_blob":None})
