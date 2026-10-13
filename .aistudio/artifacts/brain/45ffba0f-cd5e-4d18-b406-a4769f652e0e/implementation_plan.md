# Comprehensive UI Glitch, Layout Overflow & Logic Audit Plan

A complete audit and remediation plan addressing UI visual glitches, layout overflows, hardcoded color incompatibilities in the Item Details screen, White theme support, icon fixes, and core database/logic integrity.

### User Review & Critical Decisions

> [!IMPORTANT]
> Based on your diagnostic choices, this plan addresses all identified visual glitches, color theme failures, layout overflows, and logic edge cases across the app.

- **Confirmed Decision 1 (Item Details Theme Fix)**: Eliminate hardcoded dark-slate (`#0F172A`, `#1E293B`) and static white text (`Color.White`) from `ItemDetailScreen`. Bind all background, card surfaces, text elements, and buttons directly to `MaterialTheme.colorScheme` so custom themes and light mode render legibly with high contrast.
- **Confirmed Decision 2 (White & Minimal Theme Support)**: Add a dedicated "White / Minimal Crisp" preset (`#FFFFFF`) to the App Theme Color picker in Settings. Adapt `StockManagerTheme` so selecting a pure white or ultra-light theme automatically switches `onPrimary` to dark text, adds subtle crisp border outlines (`outlineVariant`), and prevents white-on-white text washing.
- **Confirmed Decision 3 (Icons & Navigation Fixes)**: Replace the top-left arrow in `ItemsScreen` with the standard Cross / Close icon (`Icons.Default.Close`) returning to Home menu. Ensure the Group By action uses a distinct hierarchical category icon (`Icons.Default.Layers` or `Icons.AutoMirrored.Filled.ViewList`) rather than the paint palette icon.
- **Confirmed Decision 4 (Layout Overflow Defenses)**: Apply flexible vertical scrolling, text ellipsis with responsive font sizing, and `weight` boundaries on the 2-column grid cards, billing tables, and dialog input forms to prevent cut-offs on compact device viewports.

---

### 1. Overview & Core Concept

- **What It Does**: Conducts a thorough cleanup across the application to eliminate visual artifacts, clipped text, broken contrast states, theme synchronization disconnects, and transaction calculation inconsistencies.
- **Target Audience / Persona**: Hardware, paint, and building materials retail store operators and counter salesmen operating on diverse Android screen sizes and brightness levels.
- **Key Value**: Provides a reliable, glitch-free interface where screens adapt to the user's selected theme, dialogs do not overflow the keyboard, and numbers/balances remain mathematically accurate.

---

### 2. User Experience & Visual Design

#### Key User Flows & Audit Targets
1. **Items Screen & Grid Cards**:
   - Top Bar: Display `Close` icon on the left leading to Home, clear screen title, and three-dot options menu (Add item, Color tint import, Camera/Scan Bill).
   - Search & Filter Row: Search bar with voice mic alongside a dedicated filter button revealing Sort, Group By (`Icons.Default.Layers`), and In-Stock toggle.
   - 2-Column Grid Cards: Product cards formatted with category pill, product image/fallback, bold title (2-line maximum with ellipsis), price in theme primary color, and stock badge. Prevent layout expansion jitter when card contents vary in height.
   - Long-press item selection gesture responsive and smooth.
2. **Item Details Screen**:
   - Hero banner with image or placeholder and round close button.
   - Product name and SKU/Category using dynamic `MaterialTheme.colorScheme.onSurface` and `onSurfaceVariant` so text is crisp across light, dark, and custom themes.
   - 2x2 Metric Matrix: Purchase cost, Selling price, Quantity in stock, and Profit margin styled using dynamic theme surface cards with clear labels and legible numbers.
   - Action buttons: "Attach Photo" and "+ New Transaction" rendered using `MaterialTheme.colorScheme.primary` and `primaryContainer`.
3. **Settings Screen Theme Selector**:
   - Preset row including: Blue, Indigo, Emerald, Rose, Amber, Violet, Teal, Slate, and **White / Crisp** (`#FFFFFF`).
   - Dynamic contrast calculation for custom hex inputs to prevent illegible buttons when light hex colors are supplied.

#### Visual Hierarchy & Theme Tokens
```
┌─────────────────────────────────────────────────────────────┐
│ Light & Custom Themes                                       │
│ ├─ Background:        MaterialTheme.colorScheme.background  │
│ ├─ Surface/Cards:     MaterialTheme.colorScheme.surface     │
│ ├─ Primary Elements:  Selected Theme Color                  │
│ └─ OnPrimary Text:    High-contrast (Dark on White,         │
│                       White on Deep Colors)                 │
└─────────────────────────────────────────────────────────────┘
```

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dynamic Surface Coloring vs Hardcoded Dark Look in Details**:
  - *Chosen Approach*: Replace hardcoded dark HEX codes in `ItemDetailScreen` with Material 3 semantic color tokens.
  - *Why*: The hardcoded dark colors prevented custom themes from taking effect and caused invisible white text on white cards in light mode.
  - *Alternatives Considered*: Enforcing dark mode on only that screen was rejected because it broke visual continuity with the rest of the application.
- **Decision 2: White Theme High-Contrast Enforcement**:
  - *Chosen Approach*: When the theme color is near-white (luminance > 0.85), automatically assign `onPrimary = Color(0xFF0F172A)` and apply a subtle dark border around primary buttons.
  - *Why*: Allows users to have a minimalist white/monochrome aesthetic without button text becoming invisible white-on-white.
- **Decision 3: Safe Layout Scroll Wrappers in Dialogs**:
  - *Chosen Approach*: Wrap `ItemFormDialog`, `StockTransactionDialog`, and calculator sheets in `rememberScrollState().verticalScroll()` with `imePadding()`.
  - *Why*: Prevents keyboard focus from obscuring input fields or causing clipped content.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                      StockManagerTheme                          │
│  (Custom Color + Dynamic Luminance Calculation + Dark/Light)   │
└────────────────┬────────────────────────────────────────────────┘
                 │ Provides Theme Tokens
                 ▼
┌──────────────────────────────────┬──────────────────────────────┐
│           ItemsScreen            │       ItemDetailScreen       │
│  - Close Icon -> Home            │  - Semantic Theme Tokens     │
│  - Filter with Layered Group Icon│  - Dynamic 2x2 Stats Card    │
│  - 2-Column Responsive Grid Cards│  - High-Contrast Badges      │
└──────────────────────────────────┴──────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              Database & Logic Verification                      │
│  - Stock Transaction Balance Integrity                         │
│  - Safe Margin Calculation (cost == 0.0 or negative stock)      │
│  - Offline Room Persistence & Firestore Sync Consistency        │
└─────────────────────────────────────────────────────────────────┘
```

#### Execution Steps upon Approval:
1. **Fix Theme Architecture (`Theme.kt`)**: Add luminance check for light/white themes so `onPrimary` switches to dark text and surface borders stay defined.
2. **Add White Theme Preset (`SettingsScreen.kt`)**: Add "Pure White" / "Crisp White" (`#FFFFFF`) to `presetColors` list.
3. **Refactor `ItemDetailScreen.kt`**: Remove hardcoded dark hex values (`0xFF0F172A`, `0xFF1E293B`, `Color.White`, `BrandBlue`); replace with `MaterialTheme.colorScheme` tokens.
4. **Refactor `ItemsScreen.kt`**: Replace top-left arrow with `Icons.Default.Close` navigating to Home; replace Group By icon with `Icons.Default.Layers`; enforce safe 2-column card sizing.
5. **Audit Dialogs & Overflows**: Verify `imePadding` and scroll bounds in `ItemFormDialog.kt` and `StockTransactionDialog.kt`.
6. **Compile & Test Verification**: Run `compile_applet` to verify compilation and clean build status.
