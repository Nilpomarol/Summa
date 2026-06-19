# Personal Finance App - Per-App Architecture

> P0A-8 internal architecture note. This narrows `docs/03-architecture.md` into the code shape each native app should use once scaffolding starts. It does not change the product rules in `docs/00-Full_Spec.md`, the sync model in `docs/02-synchronization.md`, or the data contract in `docs/04-data-model.md`.

---

## 1. Purpose

The project has two native apps over one shared finance contract:

- **Android** is the primary app and master database.
- **Windows** is the secondary app, with desktop analysis and CSV import.
- **`shared/`** owns schema, canonical SQL, and golden vectors. App code consumes those artifacts; it does not redefine them.

The app architecture should keep each platform idiomatic while making money behavior impossible to fork accidentally.

---

## 2. Architectural Principles

1. **SQL-first data layer.** Repositories read and write SQLite through the canonical schema and queries. Derived values come from the shared views (`v_account_balance`, `v_account_flow`, `v_actual_expense`, `v_actual_income`, `v_person_balance`), not from app-side recomputation.
2. **Thin domain services.** Domain code coordinates validation, write flows, and the few procedural rules that cannot live in SQL. Those procedural rules are pure functions and are covered by `shared/golden/*.json`.
3. **Platform-native UI.** Android uses Compose with MVI-flavored MVVM. Windows uses WinUI 3 with MVVM. Screen state shapes can be similar, but UI code is not shared.
4. **Single write gate.** Every write command checks whether this device holds the sync token. Read-only devices disable edit affordances and reject writes at the use-case boundary.
5. **Warnings are data.** Duplicate matches, over-refunds, live dependencies on archive, and over-settlements are returned as dismissible warnings with an explicit override path, not thrown as hard blockers.
6. **No speculative framework layer.** Use constructor injection and small composition roots first. Do not add a full application framework, ORM, event bus, mediator, or generic repository abstraction.
7. **Catalan UI, English code.** User-facing strings come from native resource catalogs. Code, package names, comments, and docs remain English.

---

## 3. Shared Contract Wiring

Expected shared layout after Phase 0A:

```text
shared/
  schema/
    schema.sql
  migrations/
    001_initial.sql
  queries/
    v_movement_shared.sql
    v_account_flow.sql
    v_account_balance.sql
    v_actual_expense.sql
    v_actual_income.sql
    v_person_balance.sql
    analysis_*.sql
  golden/
    *.json
```

The exact filenames may evolve, but the ownership rule does not: if SQL expresses finance behavior, it starts in `shared/`.

### 3.1 Build-Time Use

- **Android:** Gradle copies or links `shared/schema`, `shared/migrations`, and `shared/queries` into the SQLDelight source set. SQLDelight generates Kotlin bindings from those copied inputs. Generated files are build output and must not be edited.
- **Windows:** the .NET project includes the same `shared/schema`, `shared/migrations`, and `shared/queries` files as content or embedded resources. A small query loader reads those files and executes them through Microsoft.Data.Sqlite; Dapper maps result rows to simple DTOs.
- **Tests:** both test projects load `shared/golden/*.json` from the repository, not from duplicated app-local fixtures.

### 3.2 Parity Rules

- App-local SQL files are allowed only when they are generated or copied from `shared/`.
- Query text should not be rewritten by app code. If a placeholder style must be adjusted for a driver, fix the shared query convention or the build copy step, then prove parity in tests.
- Schema changes are atomic: shared DDL, migration, canonical SQL, golden vectors, and app bindings move together.
- The apps may add platform-specific queries only for non-finance UI concerns, such as local settings lookup. Anything that computes balances, actuals, debt, analysis totals, or account flow belongs in `shared/queries`.

### 3.3 Runtime Database Responsibilities

Each app owns:

- database file location;
- opening SQLite with `foreign_keys = ON`, WAL, and a busy timeout;
- running migrations in order;
- applying `shared/queries` view definitions;
- snapshot creation/application for sync and backup once Phase 7 starts.

The schema version and snapshot version live in the `meta` table. App code reads them through a small `MetaRepository`; it does not maintain sidecar version files for finance state.

---

## 4. Common App Layers

Both apps use the same logical layers:

```text
UI / Presentation
  Screens, view models, navigation, native resources

Application / Use Cases
  User actions, write orchestration, read-only gate, warning/override flow

Domain
  Domain models, value types, pure procedural rules, validation results

Data
  SQLite connection, migrations, SQL bindings/query loader, repositories

Shared Artifacts
  shared/schema, shared/queries, shared/golden
```

Dependency direction is one-way: UI depends on use cases, use cases depend on domain and repositories, repositories depend on SQLite/shared SQL. Data code must not call UI code, and domain rules must not depend on SQLite.

### 4.1 Domain Model Conventions

- Money is `Long`/`long` integer euro cents in code. Formatting to euros happens only at the UI edge.
- Movement dates are local calendar dates: Kotlin `LocalDate`, C# NodaTime `LocalDate` unless the Windows scaffold deliberately chooses the lighter `DateOnly` alternative documented in `03-architecture.md`.
- Audit timestamps are instants: Kotlin `Instant`, C# NodaTime `Instant` or UTC `DateTimeOffset`.
- IDs are opaque strings matching SQLite `TEXT` IDs.
- Domain models may wrap primitive types where it improves clarity (`MoneyCents`, `MovementId`), but do not create a large type hierarchy before the first vertical slices need it.

### 4.2 Repository Boundaries

Repositories are thin, named around app concepts:

- `AccountRepository`
- `CategoryRepository`
- `MovementRepository`
- `SplitRepository`
- `PersonRepository`
- `TemplateRepository`
- `AnalysisRepository`
- `MetaRepository`
- `SyncRepository` when sync starts

They should:

- execute SQLDelight queries or loaded shared SQL;
- run short SQLite transactions for multi-row writes;
- return rows, DTOs, or domain models without embedding UI state;
- expose derived values only by querying canonical views;
- avoid generic CRUD repositories and ORM-style query builders.

Multi-entity operations belong in use cases, not inside broad repositories. Example: `CreateSharedExpenseUseCase` validates the movement draft, computes split lines with the golden-tested split rule, writes movement plus split rows in one transaction, and returns warnings/effects.

### 4.3 Command Results

Write use cases return a result shape that can represent:

- success;
- validation errors that would violate schema invariants;
- warnings that the user can override;
- read-only rejection because this device lacks the sync token.

This keeps the "never block - warn" rule explicit while still preventing invalid rows such as `amount_cents <= 0` or a transfer without a destination account.

---

## 5. Android App Architecture

### 5.1 Suggested Package Shape

```text
android/app/src/main/java/.../gestorfinances/
  App.kt
  di/
    AppContainer.kt
  data/
    db/
      DatabaseDriverFactory.kt
      SqlDelightAdapters.kt
      Migrations.kt
    repository/
      AccountRepository.kt
      MovementRepository.kt
      AnalysisRepository.kt
      ...
  domain/
    model/
    rules/
      SplitCalculator.kt
      RecurrenceAdvancer.kt
      AutoCategorizer.kt
      DuplicateDetector.kt
    usecase/
  sync/
  ui/
    navigation/
    common/
    dashboard/
    movements/
    accounts/
    ...
```

This is a starting shape, not a mandate to create every folder on day one. Add packages with the vertical slice that needs them.

### 5.2 State Management

Android uses **MVI-flavored MVVM**:

- Each screen has a `ViewModel`.
- The `ViewModel` exposes a single immutable `UiState` via `StateFlow`.
- UI actions enter as explicit events or intents, such as `SaveClicked`, `AmountChanged`, `DuplicateWarningConfirmed`.
- One-shot navigation, snackbars, and dialogs use an effect stream (`Channel` or `SharedFlow`), not boolean flags that linger in state.
- Compose screens render `UiState` and send events back to the `ViewModel`; they do not call repositories directly.

Repository reads should use SQLDelight observable queries where useful:

```text
SQLDelight Query.asFlow()
  -> repository Flow<DomainRows>
  -> use case or ViewModel combines/filter maps
  -> UiState
  -> Compose
```

Writes run in coroutines on an IO dispatcher. A successful write relies on SQLite/SQLDelight invalidation to refresh dependent screens; ViewModels should not manually patch derived balances.

### 5.3 Android DI

Use manual constructor injection with a small application container:

- `AppContainer` is created from `Application`.
- It owns singletons: database driver, generated database, clock/date provider, repositories, domain rule services, use cases, and token/read-only state.
- ViewModels receive dependencies through factories.

Do not introduce Hilt or another DI framework in Phase 0/1. Revisit only if constructor wiring becomes a real maintenance cost after multiple feature slices exist.

### 5.4 Android Shared SQL Wiring

- SQLDelight schema inputs come from the copied `shared/schema` and `shared/queries`.
- Type adapters convert SQLite `TEXT` dates/instants to Kotlin date types at repository boundaries.
- Analysis and balance screens call generated bindings for the canonical views/queries.
- Golden-vector unit tests live in the Android test source set and read the repository `shared/golden` files.

### 5.5 Android UI Rules

- Compose screens use native Android string resources generated from the shared Catalan key set once that catalog is extracted.
- Form drafts may use local Compose state for purely visual fields, but saveable business drafts should live in the ViewModel so warnings, overrides, and validation are testable.
- Read-only sync state is part of global app state and is rendered as a prominent banner plus disabled edit actions.

---

## 6. Windows App Architecture

### 6.1 Suggested Project Shape

```text
windows/
  GestorFinances.App/
    App.xaml.cs
    Composition/
      AppServices.cs
    Data/
      Sqlite/
        SqliteConnectionFactory.cs
        MigrationRunner.cs
        SharedSqlLoader.cs
      Repositories/
        AccountRepository.cs
        MovementRepository.cs
        AnalysisRepository.cs
        ...
    Domain/
      Models/
      Rules/
      UseCases/
    Features/
      CsvImport/
    Presentation/
      Navigation/
      ViewModels/
      Views/
      Resources/
    Sync/
  GestorFinances.Tests/
```

The Phase 0C smoke harness can create only the `Data`, `Domain`, and test pieces first. The WinUI shell arrives later in Phase 6A.

### 6.2 State Management

Windows uses **MVVM**:

- Views are XAML and code-behind remains thin. Code-behind may handle view-only concerns, not finance behavior.
- ViewModels expose observable state and commands.
- Commands call use cases, then update view state from command results.
- Long-running work (`CSV import`, migrations, snapshot operations) runs asynchronously and reports progress through ViewModel state.
- Navigation is coordinated by a small navigation service or shell ViewModel, not by repositories or domain services.

Avoid a full MVVM framework at the start. A tiny local `ObservableObject`/`RelayCommand` is enough for early slices. If boilerplate becomes the main source of complexity, `CommunityToolkit.Mvvm` is the only helper to consider, and the scaffold change should justify the dependency.

### 6.3 Windows DI

Use constructor injection with one composition root:

- `AppServices` is built from `App.xaml.cs` at startup.
- It constructs the connection factory, query loader, migration runner, repositories, rule services, use cases, and ViewModel factories.
- Feature code receives dependencies through constructors.

Do not resolve services from a global service locator inside ViewModels or repositories. If the project later adopts `Microsoft.Extensions.DependencyInjection`, keep it hidden in the composition root and preserve constructor injection everywhere else.

### 6.4 Windows Shared SQL Wiring

- The project includes `shared/schema`, `shared/migrations`, and `shared/queries` as linked files, content, or embedded resources.
- `SharedSqlLoader` exposes named SQL text from those files.
- `MigrationRunner` applies migrations in order and updates `meta.schema_version`.
- Repositories execute canonical SQL through Microsoft.Data.Sqlite. Dapper is used only for lightweight row mapping.
- Golden-vector tests read `shared/golden/*.json` directly.

No EF Core is used in the finance layer. The finance layer must not express balances, debts, actuals, or analysis in LINQ.

### 6.5 Desktop-Only CSV Import

CSV import is a Windows feature but still uses the same domain and data rules:

- parse and map CSV rows in `Features/CsvImport`;
- create movement drafts in integer cents;
- apply auto-categorization and duplicate detection through golden-tested rule services;
- save accepted rows in one SQLite transaction with `import_batch_id`;
- surface duplicate and settlement-bridge choices as warnings/options, not hard blocks.

The CSV feature may have additional UI-specific ViewModels, but it must commit through the same movement/split/settlement use cases as manual entry wherever possible.

---

## 7. Procedural Rules

The procedural rules are deliberately small and duplicated by language:

- split rounding;
- recurring advancement;
- auto-categorization matching and tie-break;
- duplicate detection;
- refund actual handling where it cannot be expressed only by SQL;
- JSON parsing/validation for `templates.split_config` and `auto_cat_rules.conditions`.

Each rule is implemented as a pure service:

```text
input DTO + clock/date context if needed
  -> deterministic output DTO
```

No service in `domain/rules` should open the database, read resources directly, or know about UI state. Tests adapt the shared golden vectors to those pure functions.

---

## 8. Sync and Read-Only Integration

Sync implementation waits until Phase 7, but architecture hooks should exist early:

- `DeviceAccessState` or equivalent exposes whether the current device may write.
- Write use cases check the access state before validation and persistence.
- UI reads the same state to show the read-only banner and disable edit actions.
- Snapshot operations live outside normal repositories. They use SQLite backup API or `VACUUM INTO`, then encryption, as specified in `docs/02-synchronization.md` and `docs/03-architecture.md`.
- Applying a snapshot is an application-level operation: close the database, atomically replace the DB file, reopen, and refresh UI state.

There is no merge path. A snapshot is accepted only when the version rule allows it.

---

## 9. Testing Strategy

### 9.1 Shared Contract Tests

Both apps must have tests that:

- apply `shared/schema` and migrations to a fresh SQLite DB;
- create the canonical views from `shared/queries`;
- load every `shared/golden/*.json`;
- assert procedural rule outputs;
- assert SQL scenario outputs for account flow, debt balance, refund actuals, and later analysis queries.

### 9.2 App Tests

- Repository tests use real SQLite and shared SQL.
- Use-case tests can use fake repositories when the behavior under test is orchestration, warning flow, or read-only rejection.
- ViewModel tests assert `UiState` transitions and one-shot effects.
- UI smoke tests stay narrow: launch, navigate, and perform one basic happy path per completed vertical slice.

### 9.3 Parity Checks

CI should include a check that Android SQLDelight inputs and Windows SQL inputs are sourced from the same `shared/` files. The check should fail when app-local copies drift.

---

## 10. Vertical Slice Checklist

For each feature slice:

1. Update `shared/schema`, migrations, `shared/queries`, and golden vectors first if the feature changes the finance contract.
2. Add or extend repository methods over the canonical SQL.
3. Add the smallest use case that expresses the user action.
4. Add ViewModel state/events and native UI.
5. Add Catalan strings to native resources using the shared key convention.
6. Add focused tests: golden, repository, use case, ViewModel, or UI smoke depending on risk.
7. Confirm derived values are read from views, not recomputed in app code.

This keeps implementation aligned with the roadmap rule: DB to view to UI, one thin end-to-end slice at a time.

---

## 11. Non-Goals

- No shared UI layer.
- No backend service.
- No cloud database.
- No EF Core in the finance layer.
- No app-side balance/debt caches as sources of truth.
- No generic repository or mediator framework.
- No multi-user/auth/profile architecture.
- No currency abstraction for v1.

