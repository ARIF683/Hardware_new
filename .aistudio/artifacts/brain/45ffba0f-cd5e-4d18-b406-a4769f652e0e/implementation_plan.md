# S.A. Hardware Showcase UI Redesign (Home & Items Tabs)

Redesign the Home and Items tabs of the application to match the modern, polished aesthetic of the **S.A. Hardware Inventory Showcase** reference design, integrating dynamic store branding, a live 7-day Stock Flow line chart, a purple-blue hero card, high-fidelity 2x2 metric cards, category chips, and a showcase 1-column list card layout alongside a settings toggle.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The following key decisions have been aligned based on user clarification:
> - **Card Layout Flexibility**: Both the 1-column list card layout (from the reference showcase) and the 2-column grid layout will be supported, with a toggle preference placed inside the **Settings tab** (defaulting to the showcase list view).
> - **Store Identity**: The Home header dynamically loads the store name from app settings ("Good morning, [Store Name]") with subtitle and avatar.
> - **Stock Flow Trend**: The 7-day Stock Flow line chart on the Home screen calculates and plots real daily transaction quantity flow from stored transaction history.
> - **Billing Scope**: Billing UI is excluded from this overhaul per user instruction; existing Billing Hub logic remains untouched.

---

### 1. Overview & Core Concept

- **Purpose**: Transform the app's visual identity to match the professional hardware store showcase design while retaining all core business logic (stock tracking, cost/selling prices, low stock alerts, bulk actions, and multi-select).
- **Target Audience**: Store owners, cashiers, and warehouse managers tracking hardware stock, margins, and daily transaction velocity.
- **Key Deliverables**:
  1. **Home Tab Overhaul**:
     - Modern Greeting Header: Sun icon, "Good morning,", dynamic Store Name, subtitle, and profile avatar.
     - Total Stock Value Hero Card: Purple-blue gradient card with Box icon, large currency value, percentage delta, and sparkline bar icon.
     - 2x2 Metric Grid: Total Items (with today delta), Total Quantity (with today delta), Total Profit (with percentage), and Low Stock warning card.
     - Stock Flow Chart: Canvas-drawn smooth line graph displaying transaction flow over the last 7 days with date ticks and data points.
     - Recent Transactions List: High-contrast transaction rows with document icon, item name, category tag, quantity delta (+/-), and relative timestamp.
  2. **Items Tab Overhaul**:
     - Top Bar: Bold "Items" title with circular primary "+" action button.
     - Search & Filter Row: Outlined rounded search bar ("Search items, brand or code...") and filter icon button.
     - Category Chips Row: Horizontal scrolling pills ("All", "Wire", "Motor", "Hardware", etc.).
     - Showcase List Card View: Rounded card with square photo thumbnail, bold product name, sub-category dot string, two-column Cost & Sell prices, stock badge (In Stock / Low Stock), and three-dot action menu.
     - Multi-Select Integration: Long-press initiates selection; subsequent clicks toggle items; docked bottom bar provides bulk actions.
  3. **Settings Tab Enhancement**:
     - Display preference allowing users to switch between **Showcase List View** and **2-Column Grid View**.

---

### 2. User Experience & Visual Design

#### Color Palette & Mood
- **Primary / Hero Gradient**: Deep Royal Indigo to Vivid Violet (`#4F46E5` → `#7C3AED` or dynamic theme primary).
- **Background Atmosphere**: Crisp, modern surface (`#F8FAFC` in light mode) with elevated white card surfaces (`#FFFFFF`).
- **Success & Status**: Emerald Green (`#10B981`) for "+X today", "In Stock", and profit gains.
- **Alert & Danger**: Rose / Amber (`#F43F5E` / `#F59E0B`) for "Low Stock" and stock drops.
- **Card Borders**: Subtle `1.dp` borders in `MaterialTheme.colorScheme.outlineVariant` (0.5f alpha).

#### Component Layouts & Flows

```
┌────────────────────────────────────────────────────────┐
│  Home Screen (Showcase Layout)                         │
├────────────────────────────────────────────────────────┤
│ ☀️ Good morning,                                  👤  │
│ S.A. Hardware                                         │
│ Keep your stock, keep your business growing.           │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 📦 Total Stock Value                            📊 │ │
│ │ ₹17,17,246                                         │ │
│ │ ↗ +2.8% from last week                             │ │
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ ┌───────────────────────────┬────────────────────────┐ │
│ │ 📦 Total Items            │ 📋 Total Quantity      │ │
│ │    474                    │    214                 │ │
│ │    +12 today              │    +6 today            │ │
│ ├───────────────────────────┼────────────────────────┤ │
│ │ ₹  Total Profit           │ ⚠️ Low Stock          │ │
│ │    ₹1,45,666              │    3 items             │ │
│ │    ↘ -2.1%                │                        │ │
│ └───────────────────────────┴────────────────────────┘ │
│                                                        │
│ Stock Flow                         [ Last 7 days ▾ ]   │
│ ┌────────────────────────────────────────────────────┐ │
│ │   ╭───────●─────●                                  │ │
│ │  ●                                                 │ │
│ │  Oct 8  Oct 9  Oct 10  Oct 11  Oct 12  Oct 13  Oct 14│
│ └────────────────────────────────────────────────────┘ │
│                                                        │
│ Recent Transactions                         View all   │
│ ┌────────────────────────────────────────────────────┐ │
│ │ 📄 1 x 0.6ft (Hardware)                 +12  10:24 │ │
│ │ 📄 1 MOTOR BATTERY (Motor)               -1  09:32 │ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

```
┌────────────────────────────────────────────────────────┐
│  Items Screen (Showcase Layout)                        │
├────────────────────────────────────────────────────────┤
│ Items                                              (+) │
│                                                        │
│ [ 🔍 Search items, brand or code...            ] [ ⚙️ ] │
│                                                        │
│ ( All )  ( Wire )  ( Motor )  ( Hardware )  ( Tube )   │
│                                                        │
│ ┌────────────────────────────────────────────────────┐ │
│ │ ┌─────┐  0.75 MM TELCON             [ In Stock ] ⋮ │ │
│ │ │ 🖼️ │  Wire • TELCON                              │ │
│ │ └─────┘  Cost: ₹550.00   Sell: ₹750.00    Stock: 50│ │
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ ┌─────┐  1 MOTOR BATTERY            [ In Stock ] ⋮ │ │
│ │ │ 🖼️ │  Motor • 1 Motor                            │ │
│ │ └─────┘  Cost: ₹78.00    Sell: ₹96.00     Stock: 30│ │
│ └────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────┐ │
│ │ ┌─────┐  1 x 1.5ft                 [ Low Stock ] ⋮ │ │
│ │ │ 🖼️ │  Hardware • Channel                         │ │
│ │ └─────┘  Cost: ₹96.00    Sell: ₹120.00     Stock: 4│ │
│ └────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────┘
```

---

### 3. Key Product Decisions & Trade-Offs

1. **Custom Canvas Line Chart vs Heavy Third-Party Library**:
   - *Chosen Approach*: Implement a custom Compose `Canvas` smooth Bézier curve line chart for "Stock Flow".
   - *Why*: Eliminates large dependencies, renders instantaneously, supports theme-aware gradient fills, and adapts perfectly to dynamic transaction volume over any 7-day window.

2. **Dual Card Layout via Settings Toggle**:
   - *Chosen Approach*: Persist an `itemsViewLayout` setting (`"list"` vs `"grid"`) in `StockViewModel` / `UserPreferences`.
   - *Why*: Directly fulfills the user's preference to keep both options while displaying the sleek 1-column list card by default.

3. **Dynamic 7-Day Stock Flow Aggregation**:
   - *Chosen Approach*: Query the existing Room `transaction_records` table to compute net daily in/out transaction quantities for the trailing 7 days.
   - *Why*: Renders real data that updates automatically whenever the user completes stock in/out transactions or sales.

4. **Multi-Selection Compatibility in List Card**:
   - *Chosen Approach*: Integrate the exact same long-press and single-click multi-selection mechanism into the new list cards, showing selection checkboxes and the docked bottom bar when active.

---

### 4. Technical Architecture & Component Mapping

```
                       ┌─────────────────────────┐
                       │      StockViewModel     │
                       │                         │
                       │ - allItems              │
                       │ - recentTransactions    │
                       │ - stockFlow7Days (Flow) │
                       │ - itemsViewLayout (Flow)│
                       │ - isSelectionMode       │
                       └────────────┬────────────┘
                                    │
           ┌────────────────────────┼────────────────────────┐
           ▼                        ▼                        ▼
┌────────────────────┐   ┌────────────────────┐   ┌────────────────────┐
│    HomeScreen      │   │    ItemsScreen     │   │   SettingsScreen   │
│                    │   │                    │   │                    │
│ • HeaderGreeting   │   │ • HeaderWithAddBtn │   │ • LayoutModeToggle │
│ • HeroStockValue   │   │ • SearchFilterBar  │   │   (List vs Grid)   │
│ • MetricCards2x2   │   │ • CategoryChipsRow │   │ • Theme Selector   │
│ • StockFlowChart   │   │ • ItemShowcaseCard │   │ • Admin Settings   │
│ • RecentTxSection  │   │ • DockedBottomBar  │   └────────────────────┘
└────────────────────┘   └────────────────────┘
```

#### Detailed File & Component Plan

1. **`StockViewModel.kt`**:
   - Expose `stockFlow7Days`: StateFlow computing the daily net quantity delta for the past 7 calendar days from `recentTransactions`.
   - Expose `itemsViewLayout`: StateFlow (`"list"` or `"grid"`, defaulting to `"list"`).
   - Add method `setItemsViewLayout(layout: String)`.
   - Compute today's item count delta (`+12 today`) and today's quantity delta (`+6 today`).

2. **`HomeScreen.kt`**:
   - Refactor UI to feature:
     - Top Greeting Row: Sun icon + "Good morning," + Store Name + Subtitle + Profile Avatar Icon.
     - `HeroStockValueCard`: Purple/Blue gradient container, box icon, value formatted via `formatRupees`, percentage change indicator, sparkline graphic.
     - `MetricsGrid2x2`: Total Items, Total Quantity, Total Profit, Low Stock cards with matching icons and colors.
     - `StockFlowChartCard`: Smooth Canvas line chart with gradient under-fill, day point dots, Y-axis guidelines, and date labels.
     - `RecentTransactionsShowcase`: Clean transaction list cards with document icon, quantity badges, and timestamps.

3. **`ItemsScreen.kt`**:
   - Add `ItemShowcaseListCard`: Single-column list card matching the image (thumbnail on left, title + subcategory, Cost & Sell price columns, In Stock / Low Stock badge, 3-dot dropdown menu).
   - Conditional rendering between `ItemShowcaseListCard` and `ItemGridCard` based on `itemsViewLayout`.
   - Top Bar: Title "Items" and round Add button `(+)`.
   - Category filter pills row: "All", followed by unique item types/categories with active state pill styling.
   - Maintain full long-press and single-click multi-selection support.

4. **`SettingsScreen.kt`**:
   - Add "View & Layout" section with options to switch between "List View (Showcase)" and "2-Column Grid".

---

### Verification & Testing Plan

1. **Compilation Check**:
   - Run `compile_applet` to ensure syntax, imports, and Compose layout bindings compile cleanly.
2. **Robolectric & Unit Tests**:
   - Run `gradle :app:testDebugUnitTest` to verify that Room queries, StockViewModel state flows, and calculations continue to pass without regression.
3. **Visual Verification**:
   - Verify that Home screen displays the purple-blue hero card, 2x2 grid, line chart, and recent transactions.
   - Verify that Items screen displays the 1-column list cards with thumbnail, Cost, Sell, Stock count, and category chips.
   - Verify that switching layout in Settings toggles between list and grid views smoothly.
