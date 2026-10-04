# Synchronization

Status: implemented on both apps (`android/core/.../data/sync`), off until the owner pairs the two devices. The merge and the link are tested between two desktop databases over the loopback, and were tried on 2026-10-02 between the debug app on a real phone and a desktop database over Wi-Fi (pairing, both directions, the minute's round, a conflict); the desktop window's own part has not been clicked through.

Principles:

- keep the product local-first, and each app usable on its own;
- preserve atomic finance operations;
- never silently resolve conflicting financial edits;
- validate before changing anything, and change everything or nothing.

## What is merged (`RecordMerge.kt`)

Records, not databases. A merge brings into this database what the other device changed, against the *base*: what both held after their last merge.

- A record is a row with the rows that only make sense with it: a movement with its split and split lines, an account with its members, a budget with its versions. Nothing is merged inside a record.
- Changed on one side only: that side's version is taken. Changed on both, or with no base to tell: a conflict, and nothing is applied until the owner picks this device's version, the other's, or the more recent of each.
- A version that changed but is older than this device's untouched one is treated as a conflict too: it is what a device restored from an old backup looks like.
- A merge that would leave a shared account or one of its expenses against the rules its triggers keep (members that add up, financing that fits the account, a split for a shared-account expense) is refused whole, and the phone names the record to correct.
- Rows are never deleted by a merge, except those this device created inside a version that lost a conflict. Deletion in the app is archival, which is an ordinary change.
- The merge runs in one transaction with the integrity triggers and unique indexes lifted and put back before the commit, and foreign keys checked: a result the schema rejects is rolled back. `meta` is never merged; both databases must be on the same schema version.

## How it travels (`SyncLink.kt`)

Directly between the two devices on the local network; nothing leaves it.

- The computer listens (TCP and UDP port 47821) while its app is open and paired. The phone finds it by broadcast, falling back to its last address.
- Pairing is a twelve-character code shown on the computer and typed once on the phone. Both derive an AES key from it; every frame is AES-GCM, so a device without the code can read and change nothing.
- A round, driven by the phone: take the computer's snapshot, merge it here against the phone's base, send the result back; the computer merges that against the snapshot it served, keeping whatever changed on it meanwhile. Once the computer has it, the phone keeps the result as its new base (`sync-base.db`, beside its database). Only the phone keeps a base, one per computer: it can be paired with several computers on different networks (each answers discovery with an id of its own) and carries every change from one to the others as it meets them. Computers never talk to each other.

## When

While the phone's app is in front it keeps itself up to date: a round on coming to the front, then it waits for either device to change. Its own writes are noticed within a second (SQLite's `data_version` on a connection of its own); the computer's arrive at once, because the computer holds the phone's "anything new?" open and answers when its data changes. With no computer in reach it tries again every half minute. One more round runs when the app is put away. Those rounds are silent unless a conflict needs the owner; Settings has "sync now". The computer only listens, and reloads its pages after a round that changed its data. With sync on, closing its window leaves the app in the system tray, still listening (it can also start with Windows, straight to the tray); a second launch only brings the running app's window back.

A new computer is started from the phone: its first-run screen offers the pairing code instead of creating an account, and the first round brings everything over.

## Known limits

- The computer must be on with the app running, at least in the tray, and the phone's app in front, on the same network; a guest network that isolates devices will not work.
- The first time, Windows asks to let the app through the firewall on private networks.
- Two devices that each went through first-run setup have their own default categories, and merging keeps both sets.

## Later: a home server

A computer that is switched off holds its changes until it is back. The owner plans a home server (a reused PC: Proxmox, Docker, Coolify) and wants sync to run through it once it exists. Decided on 2026-10-02, not built:

- The server is one more device in this scheme: a headless program with its own database file that listens and merges as a computer does. Not a hosted database the apps write to: that would cost offline use and the record merge. Turso was checked that day and has no offline-write sync for Kotlin.
- Devices reach it over HTTPS (so Coolify can serve it like any backend, with its proxy, domain and certificate), payloads still encrypted with the pairing key; a held request replaces the held socket for change notification. The direct Wi-Fi link stays as it is.
- Its address and code are typed in each app's Settings; nothing of the owner's is in the repository. The computer becomes a client as well as a listener.
- Reached from outside through Tailscale rather than an open port.
- Build it when the server exists, not before.
