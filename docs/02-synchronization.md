# Mobile ↔ Desktop Synchronization — Design and Lifecycles

This document describes the synchronization system between the mobile app and the desktop app: the chosen model, its invariants, and the full lifecycle with all failure cases.

---

## Model summary

The design is based on a **single writer at any time**, identified by a **control token**. Whoever holds the token is the only device that can write; the other stays **read-only**. Data travels via **versioned snapshots** (checkpoints), which can be sent whenever the user wants without necessarily handing over control. Snapshots are **encrypted with a user-held key**, so they stay private even when the transport channel is third-party cloud storage (see `00-Full_Spec.md` §7.4).

This model is essentially the skeleton of a token system (single writer, zero conflicts, zero merging), with **durability** recovered through manual checkpoints instead of automatic continuous propagation. It achieves nearly the best of both classic approaches without the architectural cost of either.

### Why this model

- **Single writer → no conflicts.** Since two devices never write at the same time, no merge logic, CRDT, or conflict resolution is needed. This is the one property that truly simplifies the whole system.
- **No continuous connection.** The desktop can work with the mobile turned off or on a different network. There is no heartbeat and no background worker fighting the operating system.
- **Durability via checkpoints.** The user can save the desktop's work to the mobile whenever they want, capping the maximum loss in case of a crash to the work done since the last checkpoint.
- **Implementation simplicity.** The "hard" part (the desktop session) is code that must be written anyway: a desktop app over a local DB. Sync is only the "before" (receiving the snapshot) and the "after" (returning it), plus optional intermediate checkpoints.

---

## Early unencrypted backup precursor (`P5R-16`)

Everything below this note describes the **real** sync protocol (`Phase 7`). Before that, `P5R-16` pulls a small, deliberately narrower piece forward: a manual, single-device whole-DB backup/restore, so the user can start keeping real data in the app without waiting for Phase 7.

Scope of the `P5R-16` precursor, and how it differs from the real protocol above:

- **No control token, no read-only device state, no handoff.** There's only one active device right now (Windows doesn't exist yet), so none of the single-writer machinery this doc designs is needed yet. It's a manual "export now" / "import now" action, not sync.
- **Unencrypted.** The user's own Drive (or any folder they pick via Android's Storage Access Framework) is not a shared/third-party transport in the sense §7.4 of `00-Full_Spec.md` worries about — it's the user's own account. The key-management UX this doc assumes for the real protocol is deferred to `P7-7`, which adds encryption on top of this precursor rather than the precursor reinventing it early.
- **Reuses the snapshot production/apply mechanics**, not the file format: still a consistent single-file image via `VACUUM INTO`/the backup API, still an atomic apply (temp file → fsync → rename). But the file uses a **distinct, unencrypted format and extension** from `.gfsnap` below — never write or accept a `.gfsnap` file through the unencrypted path, and never accept the precursor's plain file through whatever eventually reads `.gfsnap`. The two must stay visibly incompatible so an unencrypted backup is never mistaken for (or silently upgraded into) a real encrypted snapshot.
- **Android compatibility fallback:** the precursor first attempts `VACUUM INTO`. On Android builds whose bundled SQLite does not support it, the app checkpoints WAL with `PRAGMA wal_checkpoint(TRUNCATE)`, closes the SQLDelight driver, copies the stable main DB file into the unencrypted backup, then rebuilds the app container/recreates the Activity so the DB is reopened cleanly. Phase 7 should prefer `VACUUM INTO`/backup API where available and keep the fallback only as an Android compatibility path.
- **Version check is a warning, not a hard reject.** The real protocol's "reject if version ≤ local" rule exists to protect single-writer ordering across two devices; a single-device manual restore has no such ordering to protect, so an older backup is allowed with a dismissible warning (never-block, per `AGENTS.md` invariant #7), not refused outright.

When Phase 7 is implemented, extend this precursor's snapshot-production/apply code path for the real protocol rather than replacing it wholesale.

---

## Concepts

| Concept | Description |
|---------|-------------|
| **Control token** | Marks which device can write. There is exactly one. The holder is the only writer; the other is strictly read-only. |
| **Snapshot / checkpoint** | A **consistent single-file image** of the DB state — produced via SQLite's online backup API or `VACUUM INTO`, *not* a raw copy of the live file — carrying a **monotonically increasing version number** stored inside the DB. This is what travels between devices, **encrypted with a user-held key** before it leaves a device. Mechanics: `03-architecture.md` §2.2. |
| **Master DB** | The reference copy, living on the mobile. The desktop works over a received copy and returns it updated. |
| **Desktop session** | The period during which the desktop holds the token and works. Marked as **open** or **cleanly closed**. |

### Fundamental invariant

> **The token holder is the only writer. Everyone else is strictly read-only.**

This invariant is what makes a checkpoint always a **safe overwrite** with no need to ever merge: when a snapshot arrives at the read-only device, that device has been unable to touch anything since it handed over control, so accepting the snapshot never overwrites local changes.

### Version rule

Every snapshot carries a monotonically increasing version number. A device **rejects** any snapshot with a version equal to or lower than the one it already holds. This prevents a late return from rolling the state back.

Applying an accepted snapshot is **atomic**: decrypt to a temp file, fsync, then rename over the local DB, so an interrupted apply never leaves a half-written database.

---

## Protocol v1 shape

This section locks the Phase 0A protocol surface. Implementation still waits until Phase 7, but app scaffolds should not invent another snapshot or token format.

### Snapshot file name

Encrypted snapshots use:

```text
gestor-finances-snapshot-v1-{snapshot_version}-{created_at_utc}-{source_device_id}.gfsnap
```

Conventions:

- `snapshot_version` is zero-padded to 12 digits, e.g. `000000000123`.
- `created_at_utc` is basic UTC form, e.g. `20260619T091530Z`.
- `source_device_id` is the local device id without braces. The device id is generated once per install and stored outside the finance DB so it is not cloned through snapshots.
- The filename is for routing and human inspection only. The authoritative version is still the `meta.snapshot_version` stored inside the decrypted DB.

### Snapshot binary format

A `.gfsnap` file is:

```text
magic bytes:       "GFV7SNAP\n"
header length:    uint32 big-endian byte length of the UTF-8 JSON header
header:           UTF-8 JSON, authenticated but not encrypted
ciphertext:       encrypted SQLite snapshot bytes, followed by the 16-byte GCM tag
```

Header JSON:

```json
{
  "format": "gestor-finances.snapshot.v1",
  "schema_version": 1,
  "snapshot_version": 123,
  "created_at_utc": "2026-06-19T09:15:30Z",
  "source_device_id": "9d6f0b6d-0b7f-4ab7-a4d5-5c4e3f2fd2c0",
  "kdf": {
    "name": "PBKDF2-HMAC-SHA256",
    "iterations": 600000,
    "salt_b64": "base64-16-random-bytes",
    "key_length_bits": 256
  },
  "aead": {
    "name": "AES-256-GCM",
    "nonce_b64": "base64-12-random-bytes",
    "tag_length_bits": 128
  },
  "plaintext": {
    "type": "sqlite-vacuum-into-image"
  }
}
```

Rules:

- The plaintext is the consistent single-file SQLite image produced by SQLite backup API or `VACUUM INTO`.
- The header bytes are passed as AES-GCM additional authenticated data (AAD). Changing the header, ciphertext, or tag makes decryption fail.
- `salt_b64` is 16 cryptographically random bytes. `nonce_b64` is 12 cryptographically random bytes and must never repeat for the same derived key.
- The snapshot encryption key is derived from the user-held sync passphrase with PBKDF2-HMAC-SHA256, 600,000 iterations, and 256-bit output. The iteration count may only move upward in a future format or migration.
- AES-256-GCM is the authenticated-encryption primitive. No unauthenticated encryption mode is allowed for snapshots.
- Header `schema_version` and `snapshot_version` are copies for routing and early rejection. After decryption, the app reads `meta.schema_version` and `meta.snapshot_version` from the SQLite image and treats those DB values as authoritative.

Creating a snapshot:

1. The writer opens a transaction and increments `meta.snapshot_version` by 1. Version gaps are allowed if export fails after this point.
2. The writer creates a stable DB image with backup API or `VACUUM INTO`.
3. The writer builds the header, encrypts the image, writes the `.gfsnap` file to a temp path, fsyncs, then atomically renames it into place.

Applying a snapshot:

1. Parse the header and reject unknown `format`, unsupported `schema_version`, unsupported crypto parameters, or a header `snapshot_version` that is not newer than the local DB.
2. Derive the key, decrypt with the header bytes as AAD, and write the SQLite image to a temp DB path.
3. Open the temp DB read-only, verify `meta.schema_version` is supported and `meta.snapshot_version` is newer than the local DB, then atomically replace the local DB.
4. Reopen the DB and refresh application state.

### Token marker

The control token is represented in the exchange location by a plaintext marker:

```text
gestor-finances-token-v1.json
```

Marker JSON:

```json
{
  "format": "gestor-finances.token.v1",
  "operation": "handoff_to_desktop",
  "session_id": "1d3bd54e-1e73-479a-91d5-96dc3a10b08b",
  "holder_role": "desktop",
  "holder_device_id": "24651d6f-c889-45d7-8f20-f8c7e06f4617",
  "snapshot_version": 123,
  "snapshot_file": "gestor-finances-snapshot-v1-000000000123-20260619T091530Z-9d6f0b6d-0b7f-4ab7-a4d5-5c4e3f2fd2c0.gfsnap",
  "updated_at_utc": "2026-06-19T09:15:31Z"
}
```

Allowed `operation` values:

| Operation | Token holder after processing | Snapshot required? |
|---|---:|---:|
| `handoff_to_desktop` | desktop | yes |
| `desktop_checkpoint` | desktop | yes |
| `return_to_mobile` | mobile | yes |
| `discard_desktop_session` | mobile | no; explicit user action only |

Rules:

- The marker is not secret. It is control metadata only.
- A marker that references a snapshot is accepted only after the referenced snapshot decrypts and passes the version checks.
- The local read-only/writer flag is platform-local app state, not authoritative finance data. The DB's `meta.snapshot_version` remains the data version authority.
- `session_id` ties all desktop checkpoints and the final return to the handoff that opened the session.
- If the mobile user discards a lost desktop session, the discarded `session_id` is recorded locally. Any later marker or snapshot from that session is presented as an interrupted-session recovery choice, never applied silently.

### Crypto references

- PBKDF2-HMAC-SHA256 with a 600,000 iteration work factor follows the [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) FIPS-compatible PBKDF2 recommendation.
- AES-GCM follows [NIST SP 800-38D](https://csrc.nist.gov/pubs/sp/800/38/d/final), which specifies GCM as an authenticated-encryption mode for block ciphers.

---

## Device states

### Mobile
- **Normal (writer)** — Holds the token. Can read and write. It is the active master DB.
- **Read-only (control handed over)** — Has handed the token to the desktop. Displays data (possibly not up to date with the desktop's in-progress work) and warns it may be stale. Does not write.

### Desktop
- **Read-only (no control)** — Default state. Can view the last received snapshot, with a staleness warning. Does not write.
- **Open session (writer)** — Holds the token. Works over its local DB, persisting to disk normally. Can emit checkpoints.
- **Session pending return** — The session was left open (e.g. due to a crash) and has work not yet returned to the mobile.

---

## Lifecycle

### 1. Normal handoff (mobile → desktop)

1. The mobile prepares a snapshot of its current state (version N) and hands over the token.
2. The mobile switches to **read-only** with a possible-staleness warning.
3. The desktop receives the snapshot, marks the **session as open** and becomes **writer**.
4. The desktop works over its local DB, persisting to disk as it edits.

### 2. Intermediate checkpoint (desktop → mobile, without handing over the token)

At any moment during the session, manually or automatically:

1. The desktop sends its current snapshot (incremented version) to the mobile **but keeps the token**.
2. The mobile receives the snapshot; if the version is newer, it updates its master DB. It stays **read-only** (control is still the desktop's).
3. The desktop keeps working.

Effect: the maximum possible loss in case of a desktop crash is capped at the work done since the last checkpoint.

### 3. Normal return (desktop → mobile, handing over the token)

1. The desktop sends the final snapshot (incremented version) and **hands over the token**.
2. The desktop marks the **session as cleanly closed** and switches to **read-only**.
3. The mobile receives the snapshot, updates the master DB and returns to **Normal (writer)**.

### 4. Atomic operations (e.g. CSV import)

Bulk CSV import must be **all-or-nothing**: we don't want to end up with half the movements on the mobile and half not. Therefore, even though other edits can be checkpointed freely, an atomic operation is applied as a single block and is only reflected in a checkpoint **once fully completed**.

---

## Failure cases

### A. The desktop crashes mid-session

- The desktop's work **persists in its local DB** (disk autosave, the DB's normal behavior).
- On reopening the app, the desktop detects an **open session not cleanly closed**: it knows it has work pending return.
- The desktop recovers its work from disk and can return it to the mobile (as a checkpoint or as a final return).
- **Maximum loss:** only the work after the last checkpoint (and, for uncompleted atomic operations, the whole operation, which is re-run).

### B. The mobile is waiting for the return (default policy: conservative)

- While a desktop session remains unclosed, the mobile **stays read-only** and does **not** reclaim the token automatically.
- Control is recovered when the desktop returns (even hours later) or when the user **manually discards** the lost session.
- **Advantage:** there are never two diverging sources of truth; no data is ever lost silently.
- **Cost:** a desktop crash leaves the mobile blocked (read-only) until the user acts.

### C. Double failure (timeout variant — optional future improvement)

If a timeout is implemented so the mobile reclaims the token automatically after a margin:

- The mobile reclaims the token and returns to **Normal (writer)**.
- If the desktop later revives with pending work, it does **not** apply it blindly (the mobile may have changed things in the meantime). Instead, it **presents** the interrupted session to the user: "You have an interrupted session with these changes, do you want to recover it?".
- The user decides. The decision is never silent.
- The version rule guarantees that a late return with an older version never rolls the state back.

---

## Recommended policy

- **Default: conservative policy (case B).** It is the simplest and never loses data. The mobile stays read-only until the desktop returns or the user discards the session.
- **Future improvement: timeout with conflict presentation (case C).** Add it only if mobile blocking turns out to be annoying in practice.
- **Checkpoints:** enable both manual and periodic automatic checkpoints during the desktop session, to minimize loss in case of a crash.
- **Atomic operations:** treat CSV import (and equivalent operations) as a single block that is only checkpointed once fully completed.

---

## Operating model & UX

This is a **manual handoff / checkpoint** model, not seamless background sync — the user explicitly passes control between devices. The UX must therefore make state unmistakable:

- The **read-only** device shows a **persistent, prominent** indicator (not a subtle badge) that editing is disabled and data may be stale, naming which device currently holds the token.
- Editing affordances (the global "New movement", edit/delete buttons) are visibly **disabled** on the read-only device, not silent no-ops.
- A **one-tap action** to **reclaim control** or **discard the lost session** (per the conservative policy, case B) is always reachable — so a desktop crash never leaves the mobile silently stuck in read-only.

## One-sentence summary

> A single writer marked by a token; the holder works over its local DB and sends versioned snapshots (checkpoints) whenever it wants, keeping the other device read-only; the handoff of control is a checkpoint that also cedes the token; each device persists its work to disk, so a crash never loses more than the work after the last checkpoint — no heartbeat, no background worker, no merging, no conflicts.
