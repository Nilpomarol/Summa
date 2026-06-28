# Redesigning Sharing and Debt UX/UI

This plan refines the sharing system to use personalized, human-centric labels and a unified "Who Paid?" flow.

## Proposed Changes

### 1. Naming & Mental Model
- **Personalized Labels:**
    - **Someone else paid for me:** "Pagat per [Nom]" (e.g., "Pagat per Ana").
    - **I paid 100% for someone else:** "He pagat per [Nom]" (e.g., "He pagat per Ana").
    - **Normal Shared:** "Compartit".
- **Form Question:** "Qui ha pagat?" (Who paid?) with options **"Jo"** (Me) and **"Un altre"** (Someone else).

### 2. Movement Form Redesign (`MovementFormSheet.kt`)
- **Type Selector:** Simplified to `Despesa`, `Ingrés`, `Transferència`.
- **"Qui ha pagat?" Section (Inside `Despesa`):**
    - **Option "Jo" (Me):**
        - Shows **Compte** (Account selector).
        - Shows **Compartit** toggle.
    - **Option "Un altre" (Someone else):**
        - Shows **Pagador** (Person picker).
        - Hides **Compte** (No ledger movement).
        - Implicitly creates an external split where the user owes the selected person.

### 3. Movement List Item Redesign (`MovementListItem.kt`)
- **Scenario A: Someone else paid for me (Debt)**
    - **Label:** "Pagat per [Nom]" (using `paidByPersonName` or `settlementPersonName`).
    - **Visuals:** `Handshake` icon, **Debt Red** amount (the debt value).
- **Scenario B: I paid 100% for someone else (Lending)**
    - **Label:** "He pagat per [Nom]" (calculated from split lines where user share = 0).
    - **Visuals:** `Groups` icon, **0,00 €** amount (neutral), with total bill as subtitle.
- **Scenario C: Normal Shared**
    - **Label:** "Compartit".
    - **Visuals:** `Groups` icon, user's share as primary amount, total bill as subtitle.

## Verification Plan

### Automated Tests
- Run `.\gradlew.bat :app:testDebugUnitTest` to ensure split calculation and repository logic remain correct.

### Manual Verification
1.  **Form:** Confirm selecting "Un altre" as payer correctly assigns the debt to the picked person and hides the account field.
2.  **List:**
    - Verify "Pagat per [Nom]" appears in Red with the Handshake icon.
    - Verify "He pagat per [Nom]" shows 0,00€ with the Groups icon and total bill.
    - Verify "Compartit" shows the user's share and total bill.
