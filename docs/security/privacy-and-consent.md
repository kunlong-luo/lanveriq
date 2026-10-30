# Privacy, notice and permitted collection

**Mandatory:** This is a managed-device product for organizations that have provided lawful, specific, prior notice to affected users. Device ownership by itself is **not** blanket consent for every form of collection. Laws differ by jurisdiction; obtain local legal review before workplace rollout.

## Required organizational policy
Define the responsible organization/contact; eligible devices and users; legitimate purpose; exact sources and data fields; collection schedule; restrictions on personal use; retention and deletion; authorized roles; review/export approval; user access/inquiry mechanism; incident escalation. Provide versioned policy acknowledgment where required, and show policy plus agent/capture state in the desktop companion.

## Data minimization
- Default collect: device health, coarse application name/active interval, and selected system audit events (allowlisted).
- Default **do not** collect: screen content, keystrokes, clipboard, passwords, authentication tokens, private chats, personal browser URLs, full window titles, microphone, webcam or arbitrary personal files.
- Application activity excludes configured personal applications and categories; OS audit directories/events are explicit allowlists. Redact file paths and command arguments if sensitive.
- Screen recording is a **separate, later feature**, only under disclosed policy and OS user-session authorization. Keep a visible capture state and native permission dialog where required. Fail closed on denial.
- Administrators cannot turn on new sensitive categories simply by changing a server flag without updating policy and satisfying OS/session permissions.

## Review controls
Viewing/export and video playback require role, scoped device/time, reason/case record and audit trail; deny by default. Use least-privilege access and bounded search. Emergency access must also be logged and separately reviewed.

## Sensitive information
Recordings may incidentally capture credentials or private data even with notice. Default audio off; minimize hours/categories, use strict retention, playback controls and incident deletion/escalation procedures. Do not train AI (none is included). No telemetry sent to vendors.

## Policy before implementation
Complete [open decisions](../governance/open-decisions.md) regarding jurisdiction, allowed fields, business hours, individual rights and retention. The product must not market "undetectable monitoring" or guaranteed capture of all user actions.
