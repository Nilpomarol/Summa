# Personal Finance App - Manual Test Checklist

> Living manual QA checklist. Use this to verify what has actually been implemented after each roadmap slice. Keep it practical: if a feature cannot be exercised yet, say so instead of inventing a test.

---

## 1. How to Use This Doc

- Run the checks for the latest completed roadmap slice and any earlier area it could affect.
- Record the app build, device/emulator, and pass/fail notes in your own test log.
- When a feature slice adds user-visible behavior, update this file in the same change with manual steps and expected results.
- Automated tests remain the release gate; this checklist is for human smoke testing and UX sanity checks.

Suggested log format:

```text
Date:
Commit:
Device / OS:
Tester:
Checks run:
Result:
Notes:
```

---

## 2. Environment Setup

### Windows / Android Local Build

Prerequisites:

- Android Studio installed.
- Android SDK installed under `%LOCALAPPDATA%\Android\Sdk`.
- Java available through Android Studio JBR.

PowerShell setup:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

Build the Android debug app:

```powershell
Push-Location android
.\gradlew.bat :app:assembleDebug
Pop-Location
```

Expected result:

- Gradle finishes with `BUILD SUCCESSFUL`.
- APK exists at `android\app\build\outputs\apk\debug\app-debug.apk`.

### Shared Contract Validators

Use any Python 3 interpreter available on your machine:

```powershell
python tools\validate_golden.py
python tools\validate_shared_sql.py
```

If your machine uses the Python launcher instead:

```powershell
py tools\validate_golden.py
py tools\validate_shared_sql.py
```

Expected result:

- Golden validator prints `validated 7 golden fixture files`.
- SQL validator prints `validated shared SQL schema, migration, and views`.

---

## 3. Current Manual Checks

### P0A - Shared Contract

Status: implemented.

Run:

```powershell
python tools\validate_golden.py
python tools\validate_shared_sql.py
```

Expected:

- Both commands pass.
- No fixture envelope errors.
- `shared/schema/schema.sql` and `shared/migrations/001_initial.sql` are executable.
- The migration seeds `meta.schema_version = 1` and `meta.snapshot_version = 0`.
- Canonical views compile: `v_movement_shared`, `v_account_flow`, `v_account_balance`, `v_actual_expense`, `v_actual_income`, `v_person_balance`.

Notes:

- This does not yet prove Android or Windows consume the SQL. That starts in P0B-2 and P0C-2.

### P0B-1 - Android Compose Scaffold

Status: implemented.

Build:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
Push-Location android
.\gradlew.bat :app:assembleDebug
Pop-Location
```

Expected:

- Build succeeds.
- Debug APK is produced.

Launch options:

- Open the `android/` folder in Android Studio and run the `app` configuration.
- Or install the debug APK on a connected device/emulator:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r android\app\build\outputs\apk\debug\app-debug.apk
```

Expected app behavior:

- App launches without crashing.
- Screen shows the title `Gestor de finances`.
- Screen shows the scaffold status text `Esquelet inicial de l'app Android.`
- There are no ledger, database, navigation, sync, or form behaviors yet.

Negative checks:

- Do not expect a database file yet.
- Do not expect SQLDelight generated code yet.
- Do not expect golden vectors to run in Android yet.

---

## 4. Future Manual Checks To Add

### P0B-2 - Android DB Open

Add once implemented:

- Fresh install creates/opens the SQLite database.
- Schema version reads as `1`.
- Snapshot version reads as `0`.
- Canonical views can be queried through SQLDelight.
- App still launches after force close/reopen.

### P0B-3 / P0B-4 - Android Procedural Rules and Golden Harness

Add once implemented:

- Android test task runs all `shared/golden/*.json`.
- Split rounding edge cases match the fixture outputs.
- Recurring advancement month-end cases match fixture outputs.
- Duplicate detection flags but does not block.
- Auto-categorization highest-priority/newest-rule behavior matches fixtures.

### Phase 1 - Core Ledger

Add once implemented:

- First-run account creation.
- Add expense, income, and transfer.
- Account balances update from `v_account_balance`.
- Movement list filters and details.
- Archive behavior hides rows without hard delete.
- Duplicate warning can be dismissed/overridden.

### Phase 3 - People and Splits

Add once implemented:

- Equal, exact, and percentage split entry.
- Remainder cents go to the payer.
- Person balances come from `v_person_balance`.
- Settlement helper pre-fills direction and amount.
- Over-settlement warns but allows override.

### Phase 7 - Sync

Add once implemented:

- Mobile handoff creates encrypted `.gfsnap` and token marker.
- Read-only device disables edit actions.
- Desktop checkpoint applies on mobile while desktop keeps token.
- Final return restores mobile write control.
- Older snapshot version is rejected.
