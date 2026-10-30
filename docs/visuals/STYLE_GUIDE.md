# Lanveriq visual language and production rules

**Status:** Design assets, not live UI. English architectural prose is canonical; the stakeholder posters use concise Simplified Chinese. The eight SVGs in [assets](assets/) are the source of truth for the **illustrations**.

## Unified canvas
- **1600 × 900 (16:9)** viewBox for every product poster.
- 62 px outer inset, 18–24 px card radius, 5–7 px colored accent strips, clear left-to-right reading direction.
- Navy top header with LANVERIQ wordmark; graphic number `VISUAL 01 / 08`; blue-green-violet semantic zones.
- Dark footer: `设计阶段 · 非真实产品截图`. Do not remove until a tested release/screenshot exists.
- No client data, staff portraits, traceable IPs, credentials, real recordings or invented performance badges.

## Brand tokens
| Token | Hex | Meaning |
|---|---|---|
| Navy | `#0B2447` | Brand header/footer |
| Ink | `#152744` | Main body labels |
| Muted | `#60738E` | Secondary explanations |
| Brand blue | `#2563EB` | Windows/Linux Agent, REST, primary |
| Cyan | `#0891B2` | Transport and local queue |
| Green | `#159A69` | Server/accepted/healthy |
| Violet | `#7C3AED` | Dashboard/WSS/restricted UI |
| Orange | `#E88816` | Storage warnings and later features |
| Red | `#DC4A55` | Failures/denials only |
| Canvas | `#F3F7FD` | Background |
| Stroke | `#D7E4F2` | Card dividers |

## Type and iconography
- SVG text uses a **system-font stack**, e.g. Inter / Noto Sans SC / Microsoft YaHei / sans-serif. **Do not commit or redistribute font binaries.**
- Recommended reading sizes for 1600×900: title 45 px; module header 27–30 px; short descriptions 18–21 px; annotations 15–17 px. Keep Chinese text short and avoid overlapping cards.
- Use consistent simple outline icon shapes, with white strokes on rounded solid tiles. Official vendor brand logos are **not necessary** inside posters; use readable technology names. Product UI later uses **Lucide React** as the primary icon library.
- Do not rely on green/red alone to communicate health. Always accompany with status words.
- Avoid flashy status animation; honor reduced-motion preference in actual React UI. Poster SVGs are deliberately static.

## Technology labels (canonical)
Endpoint: `Rust Agent`, `Tokio`, `SQLite WAL`, `HTTPS REST`; optional Tauri 2 user-session companion.

Server: `Java 25`, `Spring Boot 4`, `Spring Security`, `PostgreSQL 18`, `Flyway`, `Caddy`, `Docker Compose`.

Dashboard: `React + Vite + TypeScript`, `pnpm Workspace`, `Tailwind CSS 4`, `shadcn/ui + Radix UI`, `Lucide React`, `Motion`, `TanStack Query`, `REST + WSS`. Next.js and Electron are **not** part of the current MVP design; no AI, Redis, Kafka, Kubernetes, or external cloud is required.

## Review / change process
1. Identify impact through [Content Matrix](CONTENT_MATRIX.md).
2. Change the architecture, security or PRD source document first.
3. Update the relevant SVG **and** any derivative README/diagram labels in the same commit to `main`.
4. Verify no broken local Markdown image links and inspect SVG rendering visually in GitHub or another SVG viewer.
5. Confirm that illustration title, technology version, milestone badges, and safety/permission caveats match the actual docs.
6. Keep proposed features labeled `V0.2` or `V0.3`. Example dashboard counts are fictional and must be labeled `示例`.
7. Do not add build scripts, a CI image-render pipeline or E2E automation merely to manage pictures during the documentation phase.
