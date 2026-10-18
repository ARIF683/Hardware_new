# iOS-Style UI Overhaul: Home, Items & Navigation Bar Variants

Transform the application interface to Apple's modern **iOS 18 Human Interface Guidelines (HIG)**, replacing standard Material 3 layouts with grouped inset cards, large Cupertino navigation titles, frosted glass surfaces, and a choice among 4 distinct iOS bottom navigation bar styles.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Navbar Style Selection**: We have generated visual mockups showcasing 4 different iOS navigation bar variants. Please review the visual designs below and select your preferred navigation bar style before we proceed to coding:

### Generated Navbar Variants & In-Context Mockups

#### 1. Navigation Bar Style Comparison (4 Variants)
![iOS Navbar Variants](/app/src/main/res/drawable/ios_navbar_variants_1792260283305.jpg)

* **Type 1: Floating Translucent Pill Bar** *(Recommended)*:
  * Detached, floating capsule elevated above the screen bottom.
  * Frosted glass blur effect with subtle shadow and active indicator pill.
  * Preserves screen edge breathing room while offering a modern, sleek appearance.
* **Type 2: Classic Native iOS 18 Edge-to-Edge Tab Bar**:
  * Full-width frosted glass bar spanning the bottom of the device.
  * Hairline top separator divider with classic Apple SF-style icon and label arrangement.
  * Authentic standard iOS system feel matching Apple native apps (Music, App Store, Files).
* **Type 3: Floating Dock with Center Action Button**:
  * Curved translucent dock featuring an elevated, circular center action button (`+` Quick Bill / Scan).
  * Flanked by symmetrical navigation tabs (Home, Items, Khata, Settings).
  * Ideal for rapid high-speed billing and barcode scanning workflows.
* **Type 4: Minimalist Compact Micro-Pill**:
  * Ultra-compact, dark-frosted floating capsule with sleek glowing iconography.
  * Minimalist footprint maximizing visible vertical list space for long product inventories.

---

#### 2. In-Context Mobile Screen Comparison
![iOS Navigation In Context](/app/src/main/res/drawable/ios_nav_context_1792260298126.jpg)

Comparison showing **Type 1 (Floating Translucent Pill)** vs **Type 3 (Floating Glass Dock with Center Quick Action)** hovering over grouped inset stock cards on the store management screen.

---

#### 3. Home & Items Screen Reference Designs
* **iOS Home Screen**: Inset summary cards, quick action tiles, and live metrics.
![iOS Home UI](/app/src/main/res/drawable/ios_home_ui_1792259792589.jpg)

* **iOS Items Screen**: Segmented category controls, search pill, and grouped list rows.
![iOS Items UI](/app/src/main/res/drawable/ios_items_ui_1792259811978.jpg)

---

## 1. Overview & Core Concept

* **What It Does**: Overhauls the visual design and navigation framework of the application to mirror iOS 18 aesthetics, focusing on the **Home Screen**, **Items Screen**, and **Bottom Navigation Bar**.
* **Target Audience**: Hardware and retail store operators who prioritize speed, clean visual hierarchy, and an Apple-grade fluid touch experience.
* **Key Value**: Simplifies visual clutter, separates quick actions from passive data via inset grouped cards, and introduces modern floating/translucent tab navigation.

---

## 2. User Experience & Visual Design

### Visual Identity & Theme Tokens
* **Background Atmosphere**: Apple System Gray 6 (`#F2F2F7` light mode, `#000000` / `#1C1C1E` dark mode).
* **Cards & Surfaces**: Pure White (`#FFFFFF`) with `0.5.dp` subtle border (`#0000000D`), rounded corners (`16.dp`–`20.dp`), and soft multi-layer shadow.
* **Primary Accent**: iOS Cupertino Blue (`#007AFF`).
* **Semantic Tints**:
  * Success Green (`#34C759`): In-stock badges, revenue gains.
  * Warning Amber (`#FF9500`): Low-stock threshold alerts.
  * System Red (`#FF3B30`): Out-of-stock items, overdue khata balances.
  * Indigo Purple (`#5856D6`): Quotations and tax invoice badges.
* **Typography**:
  * **Large Navigation Title**: 30–34.sp bold with compact letter-spacing (`-0.5.sp`).
  * **Section Headers**: 13.sp uppercase bold in secondary label color (`#8E8E93`).
  * **List Row Titles**: 16.sp medium weight, 22.sp line height.
  * **Monetary Values**: 18–26.sp bold figures with rupee symbol.

---

### Screen Breakdown

#### 1. Navigation Bar (Configurable to Selected Variant)
* **Type 1 (Default / Floating Pill)**: Capsule shape with `RoundedCornerShape(32.dp)`, `88%` white translucent surface, inner border, dynamic spring-animated tab indicator.
* **Type 2 (Edge-to-Edge)**: Full-width glass bar with top hairline divider, equal-width tab slots with icon and caption.
* **Type 3 (Floating Dock with Action Button)**: Segmented dock housing 4 sub-tabs plus an elevated center action button (`+` Create Bill / Fast Scan) with ripple feedback.
* **Type 4 (Micro-Pill)**: Slim floating bar with compact icon-only representation and expandable active badge.

#### 2. iOS Home Screen (`HomeScreen.kt`)
* **Header**: Large greeting & store title + date caption + quick settings/avatar pill.
* **Metric Overview**: Grouped inset cards displaying Total Inventory, Today's Sales, and Low Stock Alerts.
* **Quick Actions Row**: Squircle action buttons for Fast Sale, Barcode Scanner, Add Product, and Quotation.
* **Recent Activity**: Inset grouped list rows with category squircles, title, quantity, and trailing chevron.

#### 3. iOS Items Screen (`ItemsScreen.kt`)
* **Large Title Bar**: Collapsible title **"Inventory & Items"** with top-trailing `+` and Barcode buttons.
* **Search Bar**: iOS filled capsule search bar (`#E5E5EA`) with magnifying glass, voice input icon, and clear trigger.
* **Category Segmented Bar**: Horizontal pill bar for instant filtering across product categories.
* **Product Rows**: Inset grouped cards featuring stock status pills (`32 in stock` in green, `Low Stock` in amber), formatted prices, and swipe/tap actions.

---

## 3. Key Product Decisions & Trade-Offs

* **Decision 1: Pure Jetpack Compose Implementation without Unofficial Wrappers**
  * *Chosen Approach*: Build custom Cupertino-styled composables (`IosFloatingNavBar`, `IosCard`, `IosSearchBar`, `IosSegmentedBar`) using standard Jetpack Compose M3 primitives.
  * *Why*: Avoids third-party dependency conflicts, guarantees 60fps animations, works seamlessly across all Android versions, and ensures zero build breakages.
* **Decision 2: Scaffold Padding & Non-Clipping Content Insets**
  * *Chosen Approach*: Pass calculated bottom insets from the floating/dock nav bar directly into screen content padding.
  * *Why*: Ensures list content scrolls completely past the floating bar so bottom items are never obscured.
* **Decision 3: Zero Business Logic Regression**
  * *Chosen Approach*: Keep Room entities, ViewModels, print managers, GST tax calculations, and barcode logic untouched; only refactor UI composables.
  * *Why*: Preserves 100% of existing functionality, quotations, and multi-page printing capabilities.

---

## 4. Technical Architecture & Component Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                          MainActivity                           │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │       Scaffold with Insets & Edge-to-Edge Support       │   │
│   │                                                         │   │
│   │   ┌─────────────────────────────────────────────────┐   │   │
│   │   │   Active Screen View (Home / Items / Billing)   │   │   │
│   │   │   - Large Title App Bar                         │   │   │
│   │   │   - Grouped Inset Cards                         │   │   │
│   │   │   - Segmented Control & Search Pill             │   │   │
│   │   │   - Safe Bottom Padding for Tab Bar             │   │   │
│   │   └─────────────────────────────────────────────────┘   │   │
│   │                                                         │   │
│   │   ┌─────────────────────────────────────────────────┐   │   │
│   │   │   Selected iOS Navigation Bar Component         │   │   │
│   │   │   (Type 1 Pill / Type 2 Native / Type 3 Dock)   │   │   │
│   │   └─────────────────────────────────────────────────┘   │   │
│   └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Component Mapping

1. **`com.example.ui.components.ios`**:
   * `IosFloatingNavBar.kt`: Reusable composable supporting Type 1 (Floating Pill), Type 2 (Edge-to-Edge), Type 3 (Center Action Dock), and Type 4 (Micro-Pill).
   * `IosCard.kt`: Inset grouped card container with HIG border, rounded corners, and elevation.
   * `IosSearchBar.kt`: Cupertino-style search input with icon and clear action.
   * `IosSegmentedBar.kt`: Sliding pill segmented selector for categories.
2. **`HomeScreen.kt`**:
   * Refactor metrics, quick action tiles, and stock list to iOS grouped layout.
3. **`ItemsScreen.kt`**:
   * Refactor search, category selector, and inventory item list rows to iOS HIG styling.
