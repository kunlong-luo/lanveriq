# GPUI candidate — offline, nonrelease
This bounded MVP-08 candidate is **not** the shipping desktop client or an installer.
It uses the upstream GPUI Kit 0.11.0 facade and its matched GPUI snapshot (0.3.14).
No direct Agent, database, process, file, network, certificate, privilege or installation interface is implemented.
The seven destinations only demonstrate native navigation and disabled actions, not migrated business functionality.
Existing Tauri code remains the rollback implementation until the acceptance in the local task ledger is complete.

The original Cargo.lock from the successful native build (d7185887) and its SHA-256 are committed together. Both CI jobs require the tracked lock, reject digest drift and never regenerate it. This is a nonrelease candidate resolution, not product license/security approval. Do not update the lock or digest merely to make CI green. The Linux normal/build graph and direct ELF dependency evidence are archived separately; neither proves a real window or all runtime dependencies. Do not distribute this candidate.
The upstream generic Ubuntu development prerequisites still include WebKitGTK; native rendering is not proof that all GTK libraries are absent from the resolved/link graph.

See [MVP task ledger](../../../docs/planning/local-mvp-tasks.md) and [ADR-0014](../../../docs/architecture/adrs/0014-gpui-desktop-candidate.md).
