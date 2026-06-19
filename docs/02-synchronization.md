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
