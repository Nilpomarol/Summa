# Windows Desktop App

Status: started. `android/desktop` opens or creates its database, restores a `.gfbackup`, runs the first-run onboarding, and has twelve pages beside a labelled navigation pane, all on ViewModels shared with Android:

- Home: net worth, the month against the plan, latest movements, accounts, and what needs attention.
- Movements: a table with search, a type switch and a panel of filters, columns that sort, months as headings, and rows that can be ticked to delete or file several at once. A row opens the phone's movement detail in a dialog, with its refund form; the full movement form is a dialog too. Any page can open them: they are hosted by the shell.
- Accounts: net worth over a table of the accounts (each one's part of what the owner holds, when it last moved, what goals reserve in it, what this month did to it, its balance; reordering). A row opens the account over the page: balance, ownership, the goals saving in it, and its ledger as a statement with what each movement did to the account and the balance it left. The balance column is counted back from the canonical balance and shown only when the canonical deltas reconcile with it.
- Recurring: tables of what is due, next, paused and ended, each row with what the item comes to over a year (a projection at today's amount). A row opens the item over the page (dates, recent payments, its payments). Its form, payment confirmation, linking and detection are dialogs, and what is due is offered once per start.
- Budgets: the month's plan and its partides beside the saving, the recent months against the plan and the yearly limits, over a table of what each partida spent month by month (desktop only).
- Analysis: the phone's figures side by side (key figures, pace, twelve months, net worth, where the money went as a table with each category's usual and its twelve-month line, the chosen category beside it), then desktop-only month-by-month tables from the same canonical queries (`AnalysisTables.kt`): every category, how the spending came about, and each account's change. A figure in the category table opens its movements (`MovementFilters.categoryIds`).
- People: the net balance over tables of who owes what (when anything last counted, the balance and its direction, settle up). A row opens the person over the page: balance, settle up (the phone's form in a dialog), an expense they paid, the message to copy, and the movements behind the balance as a table.
- Trips: a table of trips; a row opens the trip on one screen (figures and budget, spend per day over its movements by day, categories and tags beside them), where the phone has two tabs.
- Categories: a tree table with each one's part of the year, its partida, this month, its monthly average and this year. A row opens the category over the page (figures, budget, months, movements).
- Tags: tables by where they apply. A row opens the tag over the page (total, per trip, movements).
- Goals: what is reserved over the active goals as cards, each with its reserve button, and under them each account goals reserve from with what is reserved and what is free. A goal opens over the page (progress, reserve and release in the phone's form as a dialog, its reservations).
- Settings (at the foot of the pane): theme; synchronization with the phone (the pairing code, and whether the computer is listening); backups (a folder, "back up now", an automatic backup on close when something changed, whose failure is shown here the next time, the ten newest kept, restore); where the data lives; the shortcuts. These choices, and the window's size and place, live in the user's preferences (`DesktopSettings`), not in the database, so a restore does not change them.

Desktop backups are `.gfbackup` snapshots like the phone's, named `gestor-finances-desktop-backup-v1-<snapshot version>-<UTC time>` so that in a shared folder neither app prunes the other's. The phone lists and restores them but cannot read their version from the name.

The data file is looked after around the two moments it is replaced or rewritten. A restore keeps what it replaces as `before-restore.gfbackup` and puts it back by itself when the restored file cannot be opened. An older file is copied to `before-migration.gfbackup` before its schema is migrated. A file that cannot be opened at all leaves a screen that restores a backup over it, keeping the file beside it as `unreadable-<time>.db`. One app runs per user session: opening it again shows the one already running.

Pages link to each other through state the shell holds: a trip opens its tags, a tag its trip, a category the analysis, an account its goals and a goal its account.

Forms are shared, not rewritten: `EntityFormSheet` and `AppModalBottomSheet` are bottom sheets on Android and dialogs on the desktop, and the movement form body is hosted the same way. List pages are desktop layouts; detail content is the phone's. One `FinancialDataRevision` reloads the visible page after a movement write, as on Android, and after data arrives from the phone, so an open form and the page stay as they were.

Desktop pages are designed for a wide window and may go deeper than the phone (analysis especially); a phone page hosted as-is is a stopgap, not the target. The app should feel made for Windows: a navigation pane with names, visible scrollbars, keyboard shortcuts (Ctrl+N records a movement, Ctrl+1…9 and 0 open the first ten pages, Ctrl+E the tags, Ctrl+, the settings, none of them while a dialog is open; in the movement form Enter after the concept, or Ctrl+Enter anywhere, saves), tables with columns, a row opening its detail over the page.

Direction:

- Kotlin + Compose Desktop, in this repository, as a `:desktop` Gradle module beside `:app`;
- data, finance rules, and SQLDelight bindings come from `:core`, never reimplemented;
- the app works on its own: it creates its own database from the same schema and has its own onboarding;
- theme, shared components, and ViewModels are reused as they are moved to common code; screens are desktop-specific (side navigation, denser tables, multi-pane layouts, dialogs, keyboard and mouse) rather than stretched phone screens;
- restoring a `.gfbackup` from the phone works from the first version;
- CSV bank import may remain desktop-only.

Installer: `.\gradlew.bat :desktop:packageMsi` writes `desktop/build/compose/binaries/main/msi/Summa-<version>.msi` (per-user install, Start menu and desktop shortcuts, upgrades in place). It needs `JAVA_HOME` on a full JDK 17+ with `jpackage`; Android Studio's bundled runtime has none.

Order of work:

1. A UI review round with the owner: nothing has been clicked through by a person yet.
2. Trying synchronization between a real phone and this app — see [sync.md](sync.md).

Move code out of `:app` only when the desktop app needs it. The .NET harness under `windows/` is removed once the desktop build runs the `:core` tests.
