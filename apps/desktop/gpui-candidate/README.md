# GPUI candidate — offline, nonrelease
This bounded MVP-08 candidate is **not** the shipping desktop client or an installer.
It uses the upstream GPUI Kit 0.11.0 facade and its matched GPUI snapshot (0.3.14).
No direct Agent, database, process, file, network, certificate, privilege or installation interface is implemented.
The seven destinations only demonstrate native navigation and disabled actions, not migrated business functionality.
Existing Tauri code remains the rollback implementation until the acceptance in the local task ledger is complete.

CI resolves a provisional Cargo.lock and license inventory on a disposable runner, then uses that lock for tests/build. The resolution artifact is not a reviewed or committed product lock. Do not distribute or use a generated lock as license/security approval.
The upstream generic Ubuntu development prerequisites still include WebKitGTK; native rendering is not proof that all GTK libraries are absent from the resolved/link graph.

See [MVP task ledger](../../../docs/planning/local-mvp-tasks.md) and [ADR-0014](../../../docs/architecture/adrs/0014-gpui-desktop-candidate.md).
