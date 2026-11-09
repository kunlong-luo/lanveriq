#!/usr/bin/env python3
"""Local-only native RustDesk port smoke after explicit pilot container startup.

Not a real RustDesk peer handshake, authentication, ICE/UDP validation or WAN test.
Only connects to 127.0.0.1, never remote targets; no credentials or data writes.
"""
from __future__ import annotations

import json
import socket
import sys
import time


REQUIRED_TCP = (21115, 21116, 21117)
DENIED_TCP = (21114, 21118, 21119)
MAX_ATTEMPTS = 30


def try_connect(port: int) -> bool:
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=0.5):
            return True
    except OSError:
        return False


def main() -> int:
    for attempt in range(MAX_ATTEMPTS):
        if all(try_connect(p) for p in REQUIRED_TCP):
            break
        time.sleep(1)
    else:
        print(json.dumps({"result": "FAIL", "reason": "Required RustDesk local TCP listener unavailable"}), file=sys.stderr)
        return 2
    if any(try_connect(p) for p in DENIED_TCP):
        print(json.dumps({"result": "FAIL", "reason": "Forbidden pilot host TCP listener reachable"}), file=sys.stderr)
        return 2
    print(json.dumps({
        "result": "PASS_LOCAL_TCP_PROCESS_ONLY",
        "connected_host": "127.0.0.1",
        "required_tcp_ports": REQUIRED_TCP,
        "closed_tcp_ports": DENIED_TCP,
        "not_proven": ["RustDesk protocol handshake", "key distribution/peer trust",
                       "UDP/ICE/P2P", "forced relay", "real LAN", "WAN/VPN", "production readiness"],
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
