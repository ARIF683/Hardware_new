# SAHARDWARE Codebase Audit & Glitch Resolution Plan

This implementation plan outlines a full audit of the **SAHARDWARE** app to inspect and resolve potential UI visual glitches/layout overflows, database consistency checks, and state/business logic edge cases.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on the user's audit preferences, we will perform a thorough review covering UI responsiveness, database query safety, and state handling, with particular focus on fixing any layout overflows or visual alignment bugs.

- **Confirmed Audit Scope**: Full audit covering UI, database, and logic (with emphasis on UI visual glitches & layout overflows).

---

### 1. Overview & Core Concept

- **What It Does**: Systematic code review and targeted fixes across composable screens, Room database DAOs, and ViewModel state flows to eliminate visual clipping, text wrapping overflows, and unhandled database error states.
- **Target Audience / Persona**: Store owners and counter staff using SAHARDWARE on varied screen sizes (phones and foldables).
- **Key Value**: Flawless UI rendering, robust data persistence, and reliable inventory/billing calculations.

### 2. User Experience & Visual Design

- **Key Audit Areas**:
  - **Layout Overflows**: Check scrollable containers (`LazyColumn`, `verticalScroll`), responsive padding (`WindowInsets`), text ellipsis/wrapping in billing items, inventory lists, and dialogs.
  - **Material 3 Consistency**: Verify color tokens, dynamic touch target sizes (minimum 48dp), and contrast across all 15+ screens.
  - **State Feedback**: Ensure loading states, empty states, and validation errors are cleanly displayed without UI jank.

### 3. Key Product Decisions & Trade-Offs

- **Decision 1**: Targeted Code Fixes Over Rewrites
  - *Chosen Approach*: Identify specific layout or logic flaws and apply precise, safe fixes using Jetpack Compose best practices.
  - *Why*: Preserves all working functionality while solving user-reported glitch concerns.

### 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────┐
│                  UI Screens                  │
│  (HomeScreen, ItemsScreen, BillScreen, etc.) │
└──────────────────────┬───────────────────────┘
                       │ State & UI Events
┌──────────────────────▼───────────────────────┐
│              MainViewModel                   │
│     (StateFlows, Navigation, Calculators)    │
└──────────────────────┬───────────────────────┘
                       │ Coroutines / Flow
┌──────────────────────▼───────────────────────┐
│           Repository & Room DB               │
│         (Items, Transactions, Ledger)        │
└──────────────────────────────────────────────┘
```

- **Data Model & State**: Inspect reactive `Flow` emissions and safe coroutine scopes.
- **UI & Layout Verification**: Ensure proper use of `weight(1f)`, `weight` constraints in rows, `verticalScroll`, and `imePadding` for keyboard adjustments in dialogs.
