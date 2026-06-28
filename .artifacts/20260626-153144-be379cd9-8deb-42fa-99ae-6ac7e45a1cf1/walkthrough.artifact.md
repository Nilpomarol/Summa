# Walkthrough: Fixing Recurring Movement Persistence during Edit

I have fixed the bug where editing a movement would lose its "recurrent" status or fail to save a new recurrence toggle.

## Changes

### 1. Database Layer (`Movements.sq`)
- Added `template_id` to all movement summary queries.
- Updated the `updateMovement` statement to allow updating the `template_id` field. This allows linking or unlinking a movement from a recurring template during an edit.

### 2. Repository Layer (`MovementRepository.kt`)
- Updated `MovementSummary` and `mapMovementSummary` to include the `templateId`.
- Updated the `update` method to pass the `templateId` from the draft to the database.

### 3. UI Layer (`MovementsViewModel.kt`)
- **State Mapping**: Fixed `toFormState` so that when you open an edit form, the `isRecurring` toggle and `templateId` are correctly initialized from the existing movement.
- **Saving Logic**: Enhanced `attemptSave` to detect if recurrence was toggled ON for an existing movement. If so, it now correctly seeds a new template and links the movement to it. It also correctly handles unlinking if the toggle is turned OFF.
- **Shared Movements**: Updated `onEditClicked` and `toFormState` to load and map existing splits into the `SplitEditorState`. This ensures that shared expenses show their breakdown (e.g., 40€ + 30€ + 25€) immediately when edited.
    - Fixed percentage mapping so that editing a percentage-based split correctly displays the percentages.
    - Added "Paid by Other" detection so that movement-backed splits paid by someone else (Variant B) correctly identify the payer during edit.
    - Updated the database to store and retrieve the `owed_percent` field.

- **External Expenses (Paid by Other)**: Integrated "Paid by Other" as a top-level movement type.
    - When selected, you pick a **Person** instead of a bank account.
    - These expenses appear in the main "Moviments" list, labeled as "Pagat per: [Nom]".
    - They count as "Actual" expenses in analysis and update interpersonal debt, but do not affect bank balances.
    - Updated database queries to include these external items in the main ledger via UNION logic.

    - Fixed a bug where editing an external expense could fail due to using the payer's name instead of their ID. Added `payerId` to the data model.

## Verification Summary
- **Compilation**: Verified with a successful Gradle build.
- **Logic**:
    - Adding a "Pagat per un altre" movement correctly increases the user's debt to that person.
    - The movement is visible in the main list and can be edited.
    - Archiving an external expense from the main list correctly removes the debt and analysis entry.
