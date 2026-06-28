# External Expenses (Person as Payer)

Treat external expenses where a person pays for the user as a distinct flow in the movement form. Instead of selecting a bank account, the user selects the person who paid. This simplifies the entry of debts and ensures these "external" movements are clearly visible in the main list.

## Proposed Logic

When a person pays for you:
1.  **Account Replacement**: The "Account" field is replaced by a "Person" field.
2.  **No Bank Flow**: Your bank balances are untouched.
3.  **Debt Increase**: Your debt to that person increases by the amount entered.
4.  **Actual Expense**: The amount counts as an "Actual" expense in your analysis.
5.  **Simplified Split**: We don't care about others' shares. The "Amount" you enter is specifically **your part** (what you owe that person).

## Proposed Changes

### UI Layer

#### [MovementsViewModel.kt](file:///C:/Personal/Gestor-finances-V7/android/app/src/main/java/com/gestorfinances/app/ui/movements/MovementsViewModel.kt)
- **MovementType**: Use the existing `EXTERNAL_EXPENSE` (or a similar internal type) to identify these.
- **Form logic**: When the "External Payer" mode is active:
    - Disable the "Account" selector.
    - Show a "Payer (Person)" selector.
    - The `amount` field represents the user's share.
- **Saving**: Call `splitRepository.createExternalPaidByPerson` with `userShare = totalAmount`.

#### [MovementFormSheet.kt](file:///C:/Personal/Gestor-finances-V7/android/app/src/main/java/com/gestorfinances/app/ui/movements/MovementFormSheet.kt)
- Add a toggle or a specific entry in the "Type" selector for "Pagat per un altre" (Paid by another).
- Update the layout to switch between Account and Person selectors based on the type.

#### [MovementListItem.kt](file:///C:/Personal/Gestor-finances-V7/android/app/src/main/java/com/gestorfinances/app/ui/common/MovementListItem.kt)
- Display the movement with the label **"Pagat per: [Nom]"** instead of an account name.
- Add a visual indicator (e.g., a "Debt" color or a specific icon) to show it's an external liability.

### Data & SQL

#### [Movements.sq](file:///C:/Personal/Gestor-finances-V7/android/app/src/main/sqldelight/com/gestorfinances/app/data/db/Movements.sq)
- Update summary queries to `UNION` the `splits` table (External Splits) into the main movement list.
- Use `NULL` for `account_id` in these rows.
- Map `splits.payer_person_id` to a virtual column that the UI uses to show the payer's name.

---

## User Review Required

- **UX Choice**: Should this be a new button in the "Type" selector (Expense, Income, Transfer, **Paid by Other**), or a toggle inside the Expense form?
    - *My recommendation*: Put it in the **Type** selector. It's much cleaner and makes it feel like "Spending from a person's credit" rather than spending from a bank account.

## Verification Plan

### Manual Verification
1. **Add External Expense**:
   - Open "+".
   - Select Type: **"Pagat per un altre"**.
   - Select Person: **"Anna"**.
   - Enter Amount: **15€**.
   - Save.
   - **Verify**:
     - Appears in "Moviments" as "Pagat per: Anna".
     - Analysis shows 15€ expense.
     - Anna's balance shows you owe her 15€.
     - Bank account balance is unchanged.
