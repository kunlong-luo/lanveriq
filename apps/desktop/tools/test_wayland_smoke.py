import unittest
from wayland_smoke import protocol_evidence, registry_evidence, failure_summary, MAX_TRACE

TRACE = """[ 123.000] -> xdg_wm_base@3.get_xdg_surface(new id xdg_surface@7, wl_surface@5)
[ 123.001] -> xdg_surface@7.get_toplevel(new id xdg_toplevel@8)
[ 123.002] -> xdg_toplevel@8.set_title("Lanveriq Desktop - Offline Candidate")
[ 123.003] -> wl_surface@5.commit()
[ 123.004] xdg_surface@7.configure(42)
[ 123.005] -> xdg_surface@7.ack_configure(42)
[ 123.006] -> wl_surface@5.attach(wl_buffer@12, 0, 0)
[ 123.007] -> wl_surface@5.commit()
"""

class WaylandSmoke(unittest.TestCase):
    def test_exact_protocol_chain(self):
        self.assertTrue(protocol_evidence(TRACE))
        self.assertTrue(protocol_evidence(TRACE.replace("new id ", "")))

    def test_wrong_title_serial_or_surface_rejected(self):
        for bad in (TRACE.replace("Lanveriq Desktop - Offline Candidate", "Other"),
                    TRACE.replace("ack_configure(42)", "ack_configure(43)"),
                    TRACE.replace("wl_surface@5.attach", "wl_surface@6.attach"),
                    TRACE.replace("wl_buffer@12", "nil"),
                    TRACE.replace("-> xdg_surface@7.ack_configure", "xdg_surface@7.ack_configure"),
                    TRACE.replace("xdg_surface@7.configure(42)", "xdg_surface@9.configure(42)"),
                    TRACE.rsplit("-> wl_surface@5.commit()",1)[0]):
            self.assertFalse(protocol_evidence(bad))

    def test_destroy_and_unmap_do_not_prove_commit(self):
        self.assertFalse(protocol_evidence(TRACE.replace("[ 123.007]", "-> xdg_toplevel@8.destroy()\n[ 123.007]")))
        self.assertFalse(protocol_evidence(TRACE.replace("[ 123.007]", "-> wl_surface@5.attach(nil, 0, 0)\n[ 123.007]")))
        with self.assertRaises(ValueError):
            protocol_evidence("x" * (MAX_TRACE + 1))

    def test_title_after_ack_and_new_serial(self):
        line = '[ 123.002] -> xdg_toplevel@8.set_title("Lanveriq Desktop - Offline Candidate")\n'
        moved = TRACE.replace(line, "").replace("[ 123.006]", line + "[ 123.006]")
        self.assertTrue(protocol_evidence(moved))
        stale = TRACE.replace("[ 123.006]", "xdg_surface@7.configure(43)\n[ 123.006]")
        self.assertFalse(protocol_evidence(stale))

    def test_registry_and_failure_categories(self):
        globals_seen = registry_evidence("interface: 'wl_seat', version: 7, name: 2\n"
                                         "interface: 'xdg_wm_base', version: 3, name: 3\n"
                                         "interface: 'wl_compositor', version: 4, name: 4\n")
        self.assertTrue(all(globals_seen.values()))
        self.assertFalse(registry_evidence("description: 'wl_seat', version: 7")["wl_seat"])
        self.assertTrue(failure_summary("thread 'main' panicked at fixture: called Option::unwrap")["rust_panic"])
        self.assertFalse(failure_summary("ordinary text")["rust_panic"])
