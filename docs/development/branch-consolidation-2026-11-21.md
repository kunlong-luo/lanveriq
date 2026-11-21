# 2026-11-21 branch consolidation audit

Repository: `kunlong-luo/lanveriq`. Canonical development branch: `main`.

- `docs/rustdesk-integration-20261108`: PR #2 merged, no unique commits; old ref 33a450ba74f72b67c8ab5307083b662a7c63c813.
- `feat/rustdesk-dual-network-20261108`: PR #1 merged, no unique commits; old ref 6873c1c10075cbddd0acde432c4faeae60268430.
- `security/batch-a-tls13-agent-20261109`: previously diverged by 19 commits; all 15 changed paths examined. Exact source-managed fixtures/tests are retained, and active Agent/Java code in `main` supersedes the older heartbeat variants. Merge commit `a76b0fd6f5310030d9a44dba0826215308232b0e` records the old head as a second parent with the original `main` tree unchanged. All 19 commits now are ancestors of main.
- The one-time `one-time-merged-branch-cleanup.yml` workflow checks each exact old ref SHA and verifies `git merge-base --is-ancestor` before deleting only these three explicit refs. It is limited to a main-push of its own YAML path, and must be removed after confirmation.

The branch merge changed no runtime files. The separately committed UI visual refresh is `e43886a3be9524f82961a09f409df3edb7af9b0c`; its exact SHA must pass Next fast, Chromium, Pages and RD-2 gates independently. This is not a production release; v0.1 NOT RELEASE READY and real networking, device collection, automatic recovery, remote access and certificate signing remain disabled.
