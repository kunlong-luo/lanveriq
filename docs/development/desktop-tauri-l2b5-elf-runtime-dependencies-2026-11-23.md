# DESKTOP-L2B-5 — direct ELF ABI, Debian Depends and unresolved external licenses

**SPEC · 2026-11-23 · exact-SHA L1 synthetic PASS / native L2 PENDING · UNSIGNED · v0.1 NOT RELEASE READY**

## Why and what is implemented

L2B-4 successfully inventoried all three files physically delivered by a GUI-only Debian package; its payload Syft identified only one package. This does NOT describe the host's external dynamic libraries. L2B-5 builds a distinct, source SHA-bound report about direct ELF DT_NEEDED library SONAMEs, the approved runtime loader, non-executable GNU_STACK, PIE architecture and Debian control Depends. This remains a deliberately partial runtime/license analysis.

The [read-only verifier](../../apps/desktop/tools/verify_desktop_elf_runtime_evidence.py) rechecks the complete [L2B-4](desktop-tauri-l2b4-payload-sbom-evidence-2026-11-23.md) archive/Syft/receipt/hash chain before using GNU binutils readelf against the extracted GUI ELF. It never runs or dynamically loads the examined ELF (in particular does not use ldd). It rejects non-ELF64/other CPU/non-PIE, foreign program interpreter, executable GNU_STACK, malformed or unreviewed direct SONAME, RPATH/RUNPATH, AUDIT/DEPAUDIT/FILTER/AUXILIARY and TEXTREL. The direct SONAME allowlist is provisional **for test-only GUI candidates** and not evidence of trusted library origins, license approvals or secure actual runtimes.

The resulting unsigned JSON binds the exact Debian digest, verified L2B-4 payload evidence hash, ELF binary hash, Git source SHA/run/attempt, direct shared libraries and the declared Debian Depends package names. It explicitly states transitive_runtime_resolution=NOT_VERIFIED, pop_os_runtime_versions=NOT_TESTED, shared_library_license_review=OPEN, release_approved=false and install_authorized=false. This cannot serve as an official release signature.

Debian Policy [shared-library rules](https://www.debian.org/doc/debian-policy/ch-sharedlibs.html) require proper ABI symbols/shlibs analysis and dpkg-shlibdeps-based dependency accounting, beyond an ELF DT_NEEDED list. Transitive libraries, dlopen plugins, actual Pop!_OS versions, ABI compatibility, security updates and each package's redistribution license still require independent platform and legal verification before production.

## Exact-SHA evidence

- **L1 synthetic PASS:** [functional SHA a8fa17d8](https://github.com/kunlong-luo/lanveriq/commit/a8fa17d809490288688c9af663396e56e0afbd28), [Ubuntu 24.04 CI #45168649329 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45168649329). Actual 45 unittest-discovered executions = 26 existing imported Debian archive tests + 19 new ELF ABI/receipt adversarial tests. Tests compile disposable ELF with the native C compiler and inspect it with actual GNU readelf, never execute it. Negative cases include hostile RPATH, wrong interpreter, non-PIE ELF, executable stack, unreviewed SONAME, bogus metadata, swapped payload, SHA/run replay and forged authorization.
- **L2 real native Tauri pending:** [Ubuntu24 full Debian build #45168649310](https://github.com/kunlong-luo/lanveriq/actions/runs/45168649310) at identical functional SHA. Must independently succeed at strict Debian archive, both source/payload Syft, prior receipts and real Tauri ELF readelf inspection plus runtime report create+verify. Never equate L1 PASS with L2 proof. If an unreviewed SONAME appears, investigate and document the evidence before revising the allowlist.

## Unclosed legal, runtime and production conditions

Direct SONAMEs are NOT a complete runtime closure, an installed package list, a license analysis or a proven Pop!_OS 24.04 ABI. Actual host shared-library versions/closure and license obligations must be mapped with security/legal approval. Independently approved release signer/trust root, recipient verification, bounded polkit install, separate install/start/boot consent and real COSMIC hardware L3 are also NOT RUN. No official signed Debian/Agent installer is available; no installation or production features were enabled.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gates](../planning/release-gap-register.md) · [Pop!_OS installer acceptance](../testing/linux-desktop-installer-acceptance.md).
