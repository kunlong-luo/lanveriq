# ADR-0003: Disclosed, OS-authorized screen recording

**Status:** Accepted boundary | **Date:** 2026-10-30

## Decision
Screen recording is an **optional later milestone**. An identified user-session companion must present the organization monitoring policy and recording state. Respect Windows and Linux capture permission mechanisms, protected content and session/lock boundaries. Denied or unsupported capture must fail closed and produce a gap/status event; do not bypass prompts, hide capture, or persistently watch users without appropriate notice and authorization.

## Why
Company ownership does not eliminate privacy requirements. System services cannot reliably capture all Wayland or Windows desktop sessions; building a silent recorder would be unsafe and unreliable.

## Consequences
No claim of complete 24/7 recordings. Distinct capture health and heartbeat health. Administrator playback requires RBAC, case reason, audit log and retention policy.
