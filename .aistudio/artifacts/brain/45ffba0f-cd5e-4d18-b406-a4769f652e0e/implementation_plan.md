# Implementation Plan - Billing & Quotation App Refinements

Refine application UI and data synchronization across Quotations, GST Bills, Settings, and Inventory item deletion sync with Supabase.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following improvements are planned based on your feedback:
- **Confirmed / Default Choices**:
  1. **Item Deletion Sync**: Real-time snapshot listener / periodic sync check ensuring items deleted on the website (Supabase) are automatically removed in the mobile app.
  2. **Quotation Statuses**: Direct 1-tap status chips (`Draft`, `Sent`, `Accepted`, `Converted`) on each Quotation Card and form.
  3. **Padding & Bottom Cut-offs**: Comprehensive fix with bottom content padding (`contentPadding` and `windowInsetsPadding`) in `QuotationScreen`, `GstCalculatorScreen`, and `BillScreen` so grand totals are never clipped.
  4. **Status Bar Consolidation**: Remove duplicate status/top bars, leaving a single clean top header with a dropdown menu and action buttons.
  5. **Settings Page Reorganization**: Restructure into clean card sections (Account, General, Business Profile, Sync & Data, App Info).

## 1. Overview & Core Concept

- **What It Does**: Enhances usability, fixes layout padding and grand total visibility, streamlines quotation status management, unifies header navigation, and ensures robust real-time synchronization with Supabase.
- **Key Value**: Professional, polished invoicing experience with zero clipping, accurate cross-platform item sync, and intuitive settings.

## 2. User Experience & Visual Design

- **Layout & Spacing**: Generous bottom padding (`contentPadding = PaddingValues(bottom = 120.dp)`) on all LazyColumns and proper window inset padding for edge-to-edge display.
- **Header & Navigation**: Single top app bar with dropdown menu and clean branding.
- **Settings Page**: Organized into distinct Material 3 cards for Account, Preferences, Business Details, and Sync.

## 3. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                      MainActivity                      │
└──────────────────────────┬─────────────────────────────┘
                           │
       ┌───────────────────┼───────────────────┐
       ▼                   ▼                   ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│QuotationScreen│    │GstCalculator │    │SettingsScreen│
│(Status chips, │    │(Grand total  │    │(Grouped cards│
│ bottom pad)  │    │  no clip)    │    │ & clean UI)  │
└──────┬───────┘    └──────────────┘    └──────────────┘
       │
       ▼
┌──────────────┐
│Supabase DB   │ (Real-time items sync / deletion propagation)
└──────────────┘
```

- **Item Deletion Sync**: Ensure item repository or ViewModel fetches / listens for deleted items from Supabase so website deletions reflect immediately in the mobile app.
- **Quotation Status Management**: Update Quotation model and UI components to easily switch between Draft, Sent, Accepted, and Converted.
