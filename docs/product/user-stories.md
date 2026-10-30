# Personas, user stories and critical flows

## US-01 Secure enrollment
As an IT operator I create a short-lived, one-use code for an authorized endpoint. As an installer I verify the LAN server identity and device monitoring notice before pairing. Used, expired and revoked codes fail. Devices hold unique credentials, not a shared password.

## US-02 Fleet overview
As an operator I see device OS, version, online/stale/offline state, last heartbeat, queue fullness and supported/unsupported collector features. Missing data never appears as inactivity.

## US-03 Daily activity
As an auditor I search a permitted device/time window for application intervals and configured audit events. Client timezone and server ingestion timestamp are shown separately. Private window titles/URLs are not collected by default.

## US-04 Faults and replays
As an operator I disconnect a client for 15 minutes, then reconnect it. Events in the durable local queue are accepted once by the server even after timeout/retry. If quota causes loss, an explicit gap record is visible.

## US-05 Admin rights
As an owner I define user roles and device scopes. An unauthorized user cannot read other devices or video by changing URLs, and all sensitive exports/downloads are attributable.

## US-06 Recording (later milestone)
As a device user I can see a clear capture state and provide OS-required permission. As a reviewer I provide a case identifier and reason to access a specified recorded time range. Lock/logout/portal refusal produces an unavailable/gap state, not a bypass.

## US-07 Data lifecycle
As a security administrator I configure retention by data category, inspect deletion results and know whether a backup still retains data. Sensitive video is not stored in database BLOBs.

## Navigation model
Overview → Devices → Device detail (Status / Activity / Security events / Capture / Gaps) → Investigations → Settings (Policies / Users / Storage) → Admin audit log.
