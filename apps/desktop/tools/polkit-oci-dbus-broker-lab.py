#!/usr/bin/python3
"""L2B-25 isolated ROOT-SIDE system D-Bus *review* service, permanently DENY.

Only runs in a networkless disposable Ubuntu24 OCI. Source/test fixture only:
NO APT, installer, repository, key material, filesystem writes or root commands.
Uses distro dbus-python + GI main loop and upstream Polkit, not a homegrown IPC.
"""
import os
import re
import subprocess
import sys

import dbus
import dbus.mainloop.glib
import dbus.service
from gi.repository import GLib

BUS = "org.lanveriq.Agent.ReviewLab"
PATH = "/org/lanveriq/Agent/ReviewLab"
INTERFACE = BUS
PREFIX = "org.lanveriq.agent.package."
OPERATIONS = frozenset(("install", "upgrade", "remove"))
NONCE_RE = re.compile(r"[0-9a-f]{32}\Z", re.ASCII)
MAX_NONCES = 256


def kernel_identity(pid, dbus_uid):
    """Recheck Linux real/effective/saved/fs UID + non-reused PID start time."""
    if not (0 < pid < 1 << 22 and 0 < dbus_uid < 1 << 32):
        raise ValueError("root or missing bus caller")
    with open(f"/proc/{pid}/status", encoding="ascii") as file:
        line = next((row for row in file if row.startswith("Uid:")), None)
    if line is None:
        raise ValueError("kernel UID missing")
    uids = [int(value) for value in line[4:].split()]
    if len(uids) != 4 or any(value != dbus_uid for value in uids):
        raise ValueError("D-Bus user disagrees with kernel UID")
    with open(f"/proc/{pid}/stat", encoding="ascii") as file:
        stat = file.read()
    parts = stat.rsplit(") ", 1)
    if len(parts) != 2:
        raise ValueError("kernel PID stat malformed")
    start = int(parts[1].split()[19])
    if start <= 0:
        raise ValueError("kernel process start missing")
    return start


class ReviewOnlyService(dbus.service.Object):
    def __init__(self):
        self.bus = dbus.SystemBus()
        self.name = dbus.service.BusName(BUS, self.bus, do_not_queue=True)
        super().__init__(self.bus, PATH)
        self.seen = set()
        self.daemon = dbus.Interface(
            self.bus.get_object("org.freedesktop.DBus", "/org/freedesktop/DBus"),
            "org.freedesktop.DBus",
        )

    @dbus.service.method(INTERFACE, in_signature="ss", out_signature="s",
                         sender_keyword="sender")
    def Review(self, operation, nonce, sender=None):
        # No supplied caller UID/PID, package, service, URI, command or argv.
        # Method's D-Bus sender is provided by the BUS transport, not the GUI.
        if not isinstance(operation, (str, dbus.String)) or str(operation) not in OPERATIONS:
            return "INVALID_OPERATION"
        if not isinstance(nonce, (str, dbus.String)) or NONCE_RE.fullmatch(str(nonce)) is None:
            print(f"REJECTED_NONCE kind={type(nonce).__name__} "
                  f"length={len(str(nonce))} sample={str(nonce)[:40]!r}", flush=True)
            return "INVALID_NONCE"
        if not sender or not sender.startswith(":"):
            return "UNVERIFIED_PEER"
        try:
            uid = int(self.daemon.GetConnectionUnixUser(sender))
            pid = int(self.daemon.GetConnectionUnixProcessID(sender))
            start = kernel_identity(pid, uid)
            # Bind both lookups to the *same* live unique bus sender. If client
            # exits, turns over or PID is recycled, fail closed.
            if (int(self.daemon.GetConnectionUnixUser(sender)) != uid or
                    int(self.daemon.GetConnectionUnixProcessID(sender)) != pid):
                return "UNVERIFIED_PEER"
        except (dbus.DBusException, OSError, ValueError, IndexError, TypeError):
            return "UNVERIFIED_PEER"

        # dbus-python preserves wire strings as dbus.String, not always str.
        operation, nonce = str(operation), str(nonce)
        token = (uid, nonce)
        if token in self.seen:
            print("OS_PEER_REPLAY_REJECTED", flush=True)
            return "REPLAY_DENIED"
        if len(self.seen) >= MAX_NONCES:
            return "CAPACITY_DENIED"
        # Volatile replay bound is L2 ONLY. Real root broker needs a durable
        # per-transaction ledger coupled to independent signed package roots.
        self.seen.add(token)
        try:
            checked = subprocess.run(
                ["/usr/bin/pkcheck", "--action-id", PREFIX + operation,
                 "--process", f"{pid},{start},{uid}"],
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                env={},
                timeout=5,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            return "UNVERIFIED_POLICY"
        # Polkit 1 == explicitly denied. Other codes (including 0 from
        # unexpected local .rules grant) NEVER authorize any real install.
        if checked.returncode == 1:
            print(f"OS_PEER_VERIFIED operation={operation} uid={uid} "
                  "polkit=DENIED", flush=True)
            return "POLKIT_DENIED"
        print(f"OS_PEER_POLICY_UNVERIFIED operation={operation} uid={uid}",
              flush=True)
        return "NOT_APPROVED"


def main():
    if (os.geteuid() != 0 or
            os.environ.get("LANVERIQ_POLKIT_RUNTIME_L2") !=
            "disposable-oci-only" or
            not os.path.isfile("/mnt/lanveriq-policy/org.lanveriq.agent.package.policy") or
            os.path.exists("/run/systemd/system")):
        print("NOT_APPROVED: root-side D-Bus lab requires disposable OCI",
              file=sys.stderr)
        return 2
    dbus.mainloop.glib.DBusGMainLoop(set_as_default=True)
    service = ReviewOnlyService()
    print("REVIEW_ONLY_DBUS_SERVICE_READY no-APT no-signer no-install",
          flush=True)
    GLib.MainLoop().run()
    return 0


if __name__ == "__main__":
    sys.exit(main())
