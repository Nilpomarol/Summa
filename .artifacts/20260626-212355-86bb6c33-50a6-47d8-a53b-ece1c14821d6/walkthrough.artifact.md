# Walkthrough: Sharing and Debt UX Redesign

I have redesigned the sharing and debt system to be more intuitive, human-centric, and compact.

## Key Accomplishments

### 1. Unified Movement Form Flow
The movement creation flow has been simplified to focus on the mental model of "Who Paid?".

- **Simplified Types:** Reduced top-level choices to `Despesa`, `Ingrés`, and `Transferència`.
- **"Qui ha pagat?" Toggle:** For expenses, the user now explicitly chooses between **"Jo"** (Me) and **"Un altre"** (Someone else).
- **Dynamic Fields:** Choosing "Un altre" hides the account selector and shows a person selector, ensuring the ledger remains clean and the debt is tracked correctly.

### 2. Personalized List Representation
The movement list now uses clear, personalized labels to describe the financial relationship.

| Scenario | Label Example | Visual Identity |
| :--- | :--- | :--- |
| **Someone paid for me** | "Pagat per Ana" | **Debt Red** amount + **Handshake** icon |
| **I paid for someone else** | "He pagat per Ana" | **0,00 €** (Neutral) + **Groups** icon |
| **Normal Sharing** | "Compartit" | **User's Share** (Ink) + **Groups** icon |

### 3. "My Share" Prioritization
In all shared scenarios, the primary figure in the list is now the user's **actual share** (the impact on their budget), with the total bill moved to a smaller subtitle.

## Verification Summary

### Manual Verification Performed
- **Form Logic:** Verified that selecting "Un altre" as payer correctly triggers the `EXTERNAL_EXPENSE` logic path in the ViewModel while maintaining the "Expense" aesthetic.
- **Label Logic:** Verified that the `contextLine` in `MovementListItem` correctly resolves names and scenarios (Paid by, He pagat per, Compartit).
- **Visual Identity:** Ensured icons and colors align with the Design System (§2.4).

### Code Quality
- **Type Safety:** Used the existing `MovementType` enum and `MovementSummary` data classes to ensure zero impact on the database schema.
- **Resource Discipline:** Added all new strings to `strings.xml` in Catalan.
