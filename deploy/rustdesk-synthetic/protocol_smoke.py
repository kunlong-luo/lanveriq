#!/usr/bin/env python3
"""Synthetic RustDesk OSS 1.1.16 wire-protocol acceptance, no real endpoints.

Protobuf classes are GENERATED at CI time from RustDesk Server 1.1.16's pinned
hbb_common submodule proto. Do not copy the upstream AGPL schema into Lanveriq.

The clients here are two ephemeral protocol fixtures; they are NOT real RustDesk
clients. This tests hbbs UDP/TCP protocol replies and hbbr bidirectional byte
relay, but NOT encrypted desktop sessions, authentication of users, NAT/P2P,
permission controls, or a working Lanveriq UI.
"""
from __future__ import annotations

import ipaddress
import os
import secrets
import signal
import socket
import subprocess
import tempfile
import time
from pathlib import Path

import rendezvous_pb2 as rd

HBBS = Path("/opt/rustdesk/hbbs")
HBBR = Path("/opt/rustdesk/hbbr")
READY_TIMEOUT = 20.0
CLIENT_TIMEOUT = 3.0


def framed(data: bytes) -> bytes:
    """Upstream hbb_common BytesCodec header; proto payload is generated."""
    length = len(data)
    if length <= 0x3F:
        width = 1
    elif length <= 0x3FFF:
        width = 2
    elif length <= 0x3FFFFF:
        width = 3
    else:
        raise ValueError("synthetic frame too large")
    header = (length << 2) | (width - 1)
    return header.to_bytes(width, "little") + data


def read_exact(sock: socket.socket, size: int) -> bytes:
    output = bytearray()
    while len(output) < size:
        part = sock.recv(size - len(output))
        if not part:
            raise AssertionError(f"unexpected EOF while reading {size} bytes")
        output.extend(part)
    return bytes(output)


def read_frame(sock: socket.socket) -> rd.RendezvousMessage:
    first = read_exact(sock, 1)[0]
    width = (first & 0x03) + 1
    head = bytes([first]) + read_exact(sock, width - 1)
    size = int.from_bytes(head, "little") >> 2
    if size > 128 * 1024:
        raise AssertionError("unbounded response frame")
    result = rd.RendezvousMessage()
    result.ParseFromString(read_exact(sock, size))
    return result


def send_message(sock: socket.socket, message: rd.RendezvousMessage) -> None:
    sock.sendall(framed(message.SerializeToString()))


def server_ip() -> str:
    ip = socket.gethostbyname(socket.gethostname())
    parsed = ipaddress.ip_address(ip)
    if parsed.is_loopback or parsed.is_unspecified or not parsed.is_private:
        raise AssertionError(f"refusing to test on a non-private Docker address: {ip}")
    return ip


def connect(host: str, port: int) -> socket.socket:
    return socket.create_connection((host, port), timeout=CLIENT_TIMEOUT)


def wait_tcp(host: str, port: int, process: subprocess.Popen[bytes]) -> None:
    until = time.monotonic() + READY_TIMEOUT
    while time.monotonic() < until:
        if process.poll() is not None:
            raise AssertionError(f"{process.args[0]} exited before listening: {process.returncode}")
        try:
            with connect(host, port):
                return
        except OSError:
            time.sleep(0.2)
    raise AssertionError(f"port {port} on internal Docker interface never listened")


def test_tcp_nat(host: str) -> None:
    # 21115 is the upstream NAT testing TCP listener (non-loopback path).
    with connect(host, 21115) as endpoint:
        send_message(
            endpoint,
            rd.RendezvousMessage(test_nat_request=rd.TestNatRequest(serial=0)),
        )
        reply = read_frame(endpoint)
        if not reply.HasField("test_nat_response"):
            raise AssertionError("hbbs did not return a TestNatResponse on TCP 21115")
        if reply.test_nat_response.port != endpoint.getsockname()[1]:
            raise AssertionError("TCP NAT response does not reflect the synthetic source port")
    print("PASS: hbbs TCP TestNatRequest/TestNatResponse via upstream protobuf")


def test_udp_peer_registration(host: str) -> None:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as endpoint:
        endpoint.bind((host, 0))
        endpoint.settimeout(CLIENT_TIMEOUT)
        # Upstream udp.rs uses tokio_util::codec::BytesCodec, meaning raw
        # Protobuf per datagram. TestNatRequest is NOT an hbbs UDP handler;
        # it is supported by TCP 21115 / the main TCP listener.
        peer_id = "lanveriq-ci-" + secrets.token_hex(8)
        register = rd.RendezvousMessage(
            register_peer=rd.RegisterPeer(id=peer_id, serial=0)
        )
        endpoint.sendto(register.SerializeToString(), (host, 21116))
        data, _ = endpoint.recvfrom(4096)
        response = rd.RendezvousMessage.FromString(data)
        if not response.HasField("register_peer_response"):
            raise AssertionError("hbbs did not process synthetic UDP RegisterPeer")
        if not response.register_peer_response.request_pk:
            raise AssertionError("unknown rendezvous peer must be asked for its public key")

        # An invalid/short peer identifier cannot register a public key.
        bad = rd.RendezvousMessage(
            register_pk=rd.RegisterPk(
                id="x", uuid=secrets.token_bytes(16), pk=secrets.token_bytes(32)
            )
        )
        endpoint.sendto(bad.SerializeToString(), (host, 21116))
        data, _ = endpoint.recvfrom(4096)
        rejected = rd.RendezvousMessage.FromString(data)
        if not rejected.HasField("register_pk_response"):
            raise AssertionError("hbbs did not return RegisterPkResponse for invalid ID")
        if rejected.register_pk_response.result != rd.RegisterPkResponse.UUID_MISMATCH:
            raise AssertionError("hbbs did not reject invalid-length public-key registration")
    print("PASS: hbbs UDP synthetic RegisterPeer and negative RegisterPk responses")
    print("SCOPE: rendezvous registration only; not Lanveriq certificate enrollment")


def test_relay_wrong_key(host: str) -> None:
    with connect(host, 21117) as endpoint:
        probe = rd.RendezvousMessage(
            request_relay=rd.RequestRelay(
                uuid=secrets.token_hex(16), licence_key="invalid-key-ci"
            )
        )
        send_message(endpoint, probe)
        endpoint.settimeout(CLIENT_TIMEOUT)
        if endpoint.recv(1) != b"":
            raise AssertionError("hbbr accepted a relay request with wrong server key")
    print("PASS: hbbr closes synthetic RequestRelay with an incorrect key")


def test_bidirectional_relay(host: str, public_key: str) -> None:
    # Both sockets are protocol fixtures in one private test namespace.
    # The matching public server key is NOT an administrator authorization.
    token = secrets.token_hex(24)
    req = rd.RendezvousMessage(
        request_relay=rd.RequestRelay(
            uuid=token, licence_key=public_key,
        )
    )
    with connect(host, 21117) as first:
        send_message(first, req)
        time.sleep(0.1)
        with connect(host, 21117) as second:
            send_message(second, req)
            # The upstream hbbr switches both framed TCP streams into raw
            # forwarding after it pairs two equal request_relay UUIDs.
            time.sleep(0.5)
            first_payload = b"lanveriq-ci-synthetic-left-" + secrets.token_bytes(48)
            second_payload = b"lanveriq-ci-synthetic-right-" + secrets.token_bytes(47)
            first.sendall(first_payload)
            if read_exact(second, len(first_payload)) != first_payload:
                raise AssertionError("left-to-right relay data mismatch")
            second.sendall(second_payload)
            if read_exact(first, len(second_payload)) != second_payload:
                raise AssertionError("right-to-left relay data mismatch")
    print("PASS: real hbbr 1.1.16 bidirectional raw byte relay for two synthetic clients")
    print("SCOPE: NOT desktop input, video, end-to-end encryption or Spring RBAC")


def main() -> None:
    host = server_ip()
    with tempfile.TemporaryDirectory(prefix="lanveriq-rd-protocol-") as workdir:
        root = Path(workdir)
        processes: list[subprocess.Popen[bytes]] = []

        def start(executable: Path, *args: str) -> subprocess.Popen[bytes]:
            result = subprocess.Popen(
                [str(executable), *args],
                cwd=root,
                env={**os.environ, "RUST_LOG": "warn", "TEST_HBBS": "no"},
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                start_new_session=True,
            )
            processes.append(result)
            return result

        try:
            hbbs = start(HBBS, "-k", "-", "-r", "127.0.0.1:21117")
            wait_tcp(host, 21116, hbbs)
            keyfile = root / "id_ed25519.pub"
            until = time.monotonic() + 5.0
            while not keyfile.is_file() and time.monotonic() < until:
                time.sleep(0.1)
            if not keyfile.is_file():
                raise AssertionError("hbbs did not write its ephemeral public key")
            public_key = keyfile.read_text(encoding="ascii").strip()
            if len(public_key) < 32:
                raise AssertionError("invalid generated synthetic public key")

            hbbr = start(HBBR, "-k", "-")
            wait_tcp(host, 21117, hbbr)

            test_tcp_nat(host)
            test_udp_peer_registration(host)
            test_relay_wrong_key(host)
            test_bidirectional_relay(host, public_key)
            if hbbs.poll() is not None or hbbr.poll() is not None:
                raise AssertionError("RustDesk service stopped during protocol tests")
        finally:
            for p in reversed(processes):
                if p.poll() is None:
                    os.killpg(p.pid, signal.SIGTERM)
                    try:
                        p.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        os.killpg(p.pid, signal.SIGKILL)
                        p.wait(timeout=5)


if __name__ == "__main__":
    main()
