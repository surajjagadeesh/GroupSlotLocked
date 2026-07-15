# Group Slot Locked — Implementation Plan

> RuneLite plugin for group ironman teams with per-slot token claims.
> Each player may hold **at most 5** slot tokens in personal bank/inventory, equip **at most 5** gear slots, and may only equip a slot if they hold that slot's token (not in group storage).

---

## Table of Contents

0. [Multi-agent workflow](#multi-agent-workflow)
1. [Goals & Rules](#1-goals--rules)
2. [Critical Constraints](#2-critical-constraints)
3. [Slot Model](#3-slot-model)
4. [Token Items (Slot Claims)](#4-token-items-slot-claims)
5. [Architecture Overview](#5-architecture-overview)
6. [Slot Availability UI](#6-slot-availability-ui)
7. [Validation Logic](#7-validation-logic)
8. [Visual Feedback](#8-visual-feedback)
9. [Custom Slot Icons & Display Names](#9-custom-slot-icons--display-names)
10. [Config](#10-config)
11. [Suggested File Structure](#11-suggested-file-structure)
12. [Implementation Phases](#12-implementation-phases)
13. [Skeleton Cleanup (Do First)](#13-skeleton-cleanup-do-first)
14. [Testing — How to Verify](#14-testing--how-to-verify)
15. [Open Questions](#15-open-questions)

---

## Multi-agent workflow

Use GitHub-style checkboxes (`- [ ]` / `- [x]`) to track progress. **Multiple agents can work in parallel** on different workstreams; avoid two agents on the same checkbox without coordinating.

### Conventions

| Mark | Meaning |
|------|---------|
| `- [ ]` | Not started |
| `- [x]` | Implemented and verified (see [§14](#14-testing--how-to-verify) for how to verify) |
| `- [~]` | In progress (optional; replace with `[x]` when done) |

**Task IDs** (e.g. `P0-3`, `VAL-2`) are stable references for handoffs in chat or PR descriptions.

When completing a task:
1. Implement the code.
2. Run the relevant verification from [§14](#14-testing--how-to-verify).
3. Change `[ ]` → `[x]` in this file in the **same PR/commit** as the code (or immediately after if user prefers separate doc commits).

### Parallel workstreams (minimal overlap)

| Workstream | Task prefix | Can start after | Touches mainly |
|------------|-------------|-----------------|----------------|
| Skeleton / build | `P0-*` | immediately | config, gradle, properties |
| Models + state | `STATE-*` | `P0-*` | `SlotType`, `SlotStateService`, `LocalSlotState` |
| Validation | `VAL-*` | `STATE-1` (`SlotType`) | `SlotValidator` |
| Overlays | `OVR-*` | `VAL-*` | `ItemRestrictionOverlay`, `ViolationOverlay` |
| Menu handler | `MENU-*` | `STATE-1`, `VAL-*` | `SlotMenuHandler` |
| Sidebar panel | `UI-*` | `STATE-*`, `VAL-*` | `GroupSlotLockedPanel` |
| Icons + names | `ICON-*`, `NAME-*` | `UI-1` (panel exists) | `SlotDisplayService`, resources |
| Config + plugin wire-up | `CFG-*` | `P0-*` | `GroupSlotLockedConfig`, plugin lifecycle |
| Polish / hub | `POL-*` | Phases 1–2 done | README, cleanup |

**Dependency rule:** Do not mark `VAL-*` done until `SlotValidator` unit tests pass. Do not mark `OVR-*` / `MENU-*` / `UI-*` done until `./gradlew run` manual checks for that area pass.

---

## 1. Goals & Rules

### Rules (enforced visually by the plugin)

| Rule | Description |
|------|-------------|
| **Max 5 tokens held** | At most **5** of the 11 slot token items may be in **personal bank + inventory** combined (not equipment, **not group storage**). |
| **Max 5 equipped** | At most **5** of the 11 tracked gear slots may be filled at any time. |
| **Slot claim tokens** | A player may only equip a slot if they hold that slot's token in personal bank or inventory **and** total held tokens ≤ 5. |
| **Accidental equip prevention** | Token items have Wear/Wield **deprioritized**; default left-click is **Examine** with slot-specific label text (see [§8.E](#e-static-left-click--menu-deprioritization)). |
| **Slot availability UI** | Sidebar shows which slots you can currently use (see [§6](#6-slot-availability-ui)). |

**No party plugin / no teammate sync.** Slot exclusivity across players is enforced by token distribution (each teammate holds different tokens), not by networking.

### What the plugin does NOT do

- It **cannot** prevent equipping on the OSRS server. Enforcement is **client-side only** (visual warnings, optional click blocking — see [§2](#2-critical-constraints)).
- It does not modify game data or communicate with Jagex servers beyond what RuneLite already does.

---

## 2. Critical Constraints

Read `AGENTS.md` before implementing. Key restrictions that affect design:

| Constraint | Impact on this plugin |
|------------|----------------------|
| **Client-side only** | Rules are advisory; teammates coordinate token distribution out of band. Document this in the plugin description. |
| **No party / networking** | All state is local. No `PartyPlugin`, `WSClient`, or custom websocket messages. |
| **No conditional menu entry removal (NPC type, friend status, etc.)** | OK to **deprioritize** Wear/Wield on token item IDs and on items failing equip validation. Do **not** remove entries entirely. |
| **No adding menu entries that send actions to the server** | Only reorder/deprioritize existing options. |
| **No resizing equipment/inventory click zones** | Overlays must not change click targets. |
| **No input injection** | Cannot auto-unequip illegal gear. |
| **Use `net.runelite.api.gameval` constants** | `ItemID`, `InventoryID`, `InterfaceID`, etc. — never magic numbers. |
| **No reflection, no Thread.sleep, no blocking IO on client thread** | Standard RuneLite rules apply. |

### Recommended enforcement strategy (hub-safe)

1. **Preventive (menu):** Statically deprioritize Wear/Wield on the 11 token items; deprioritize Wear/Wield on gear that fails equip validation (see [§8.E](#e-static-left-click--menu-deprioritization)).
2. **Preventive (visual):** Red overlay on inventory/equipment items that would violate rules if equipped.
3. **Reactive (hard):** Full-screen black overlay (inventory/equipment panels remain visible) when the player currently wears an illegal loadout.
4. **Optional backup (config, default off):** Consume `MenuOptionClicked` for Wear/Wield/Equip if a deprioritized option still gets clicked.

---

## 3. Slot Model

Track these **11 gameplay slots** (exclude cosmetic internal slots `HAIR`, `JAW`, `ARMS`):

| Slot enum (custom) | `EquipmentInventorySlot` | OSRS slot name |
|--------------------|--------------------------|----------------|
| `HEAD` | `HEAD` | Head |
| `CAPE` | `CAPE` | Cape |
| `NECK` | `AMULET` | Amulet/Neck |
| `AMMO` | `AMMO` | Ammo |
| `BODY` | `BODY` | Body |
| `LEGS` | `LEGS` | Legs |
| `MAIN_HAND` | `WEAPON` | Weapon |
| `OFF_HAND` | `SHIELD` | Shield/Off-hand |
| `BOOTS` | `BOOTS` | Boots |
| `RING` | `RING` | Ring |
| `GLOVES` | `GLOVES` | Gloves |

### Reading local equipment

```java
ItemContainer worn = client.getItemContainer(InventoryID.WORN);
// worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx()) etc.
```

Subscribe to `ItemContainerChanged` where `event.getContainerId() == InventoryID.WORN`.

### Mapping an inventory item → slot(s)

Use `ItemManager` / `ItemComposition`:

- `getWearPos1()`, `getWearPos2()`, `getWearPos3()` — an item may occupy multiple slots (e.g. 2H weapon fills weapon + shield).
- When validating "equip this item", compute which tracked slots it would occupy.
- Two-handed weapons occupy `MAIN_HAND` + `OFF_HAND`; treat both as filled.

### Implementation tasks

- [x] **STATE-1** — Create `SlotType` enum: 11 values, `toEquipmentSlot()`, `tokenItemId()`, `fromTokenItemId()`, `isTokenItem()` (display text comes from `SlotDisplayService` — see [§9](#9-custom-slot-icons--display-names))
- [x] **STATE-2** — Map `ItemComposition` wear positions → `Set<SlotType>` helper (`slotsForItem(int itemId)`)

---

## 4. Token Items (Slot Claims)

Each slot has a **dedicated token item**. If the token exists in the player's **personal bank or inventory**, that player is allowed to equip items in that slot.

**Does not count as a claim:**
- Tokens **equipped** on the character
- Tokens in **group ironman shared storage** (`InventoryID.GROUP_STORAGE`) — including while transferring between teammates

This matches how teams typically hand off slot tokens: deposit into group storage → teammate withdraws to bank/inventory → claim becomes active.

### Recommended tokens: Team capes 6, 16, 26, 36, 46, 10, 20, 30, 40, 50, 7

Cheap, tradable, visually distinct, easy to obtain. Use `net.runelite.api.gameval.ItemID` (`WILDERNESS_CAPE_N` aliases):

| Slot | Token item | Notes |
|------|------------|-------|
| HEAD | `WILDERNESS_CAPE_6` | Team-6 cape |
| CAPE | `WILDERNESS_CAPE_16` | Team-16 cape |
| NECK | `WILDERNESS_CAPE_26` | Team-26 cape |
| AMMO | `WILDERNESS_CAPE_36` | Team-36 cape |
| BODY | `WILDERNESS_CAPE_46` | Team-46 cape |
| LEGS | `WILDERNESS_CAPE_10` | Team-10 cape |
| MAIN_HAND | `WILDERNESS_CAPE_20` | Team-20 cape |
| OFF_HAND | `WILDERNESS_CAPE_30` | Team-30 cape |
| BOOTS | `WILDERNESS_CAPE_40` | Team-40 cape |
| RING | `WILDERNESS_CAPE_50` | Team-50 cape |
| GLOVES | `WILDERNESS_CAPE_7` | Team-7 cape |

> **Note:** Using capes for non-cape slots is intentional — they're dummy claim markers, not literal slot types. Document this for players (sidebar panel or config description).

Define a constant map in code:

```java
public enum SlotType {
    HEAD, CAPE, NECK, AMMO, BODY, LEGS, MAIN_HAND, OFF_HAND, BOOTS, RING, GLOVES;

    public EquipmentInventorySlot toEquipmentSlot() { ... }
    public int tokenItemId() { ... }  // returns gameval ItemID
}
```

### Detecting token ownership

Claims are **not** read once at team start. The plugin re-scans inventory and bank **continuously** so token pickups, trades, drops, and bank deposits/withdrawals are reflected within a tick or two.

#### Refresh strategy

Use **events first**, plus a **lightweight periodic tick refresh** so nothing is missed when the client does not fire `ItemContainerChanged` (edge cases, UI-only updates, delayed bank loads).

| Trigger | What to refresh | Notes |
|---------|-----------------|-------|
| `ItemContainerChanged` (INVENTORY) | Token claims | Primary path for pickups, drops, trades |
| `ItemContainerChanged` (BANK) | Token claims | Deposits / withdrawals |
| `ItemContainerChanged` (WORN) | Equipped mask + legality | Equipment changes |
| `GameTick` (every tick) | Inventory + worn | Cheap — small containers; keeps UI in sync |
| `GameTick` (every 10 ticks) | Bank (if loaded) | Bank container is null until opened at least once |
| Bank interface open/close | Bank claims | `ScriptPostFired` or widget visibility — force full bank rescan |
| `GameStateChanged` → `LOGGED_IN` | Full local state | Login / hop |

```java
@Subscribe
public void onGameTick(GameTick tick) {
    slotStateService.refreshInventoryAndWorn();           // every tick
    if (tick.getTickCount() % 10 == 0) {
        slotStateService.refreshBankIfAvailable();        // bank may be null
    }
}
```

Implementation rules:

- Maintain `EnumSet<SlotType> claimedSlots` and `int heldTokenCount` (0–11, must be ≤ 5 for claims to be valid).
- On each refresh, scan items and match by canonical item ID (`ItemManager.canonicalize()`).
- Union claims from **personal inventory + personal bank only** into a single set.
- Do **not** scan `InventoryID.WORN` for tokens.
- Do **not** scan `InventoryID.GROUP_STORAGE` or `InventoryID.GROUP_STORAGE_INV` for tokens — items there grant **no** slot claims.
- Compare to previous mask; only notify overlays / sidebar when the mask **changes** (avoid redundant work).
- Do **not** scan the entire scene or walk every container every frame — only personal inventory, personal bank (when available), and worn.

#### Group storage exclusion (important for transfers)

Group storage is the primary way teammates trade slot tokens. While a token sits in shared storage, **no one** can use that slot claim:

| Token location | Head slot claim? |
|----------------|------------------|
| Player A personal bank | ✅ A can equip head |
| Player A inventory | ✅ A can equip head |
| Player A equipped | ❌ |
| Group storage (deposit for transfer) | ❌ A loses claim until withdrawn |
| Player B withdraws to personal bank | ✅ B can equip head |

Implementation:

```java
// Claim sources — ONLY these two containers:
private void refreshClaims() {
    claimedSlots.clear();
    addTokensFromContainer(client.getItemContainer(InventoryID.INVENTORY));
    addTokensFromContainer(client.getItemContainer(InventoryID.BANK));
    // intentionally skip GROUP_STORAGE and GROUP_STORAGE_INV
}
```

**New UX (recommended):** Track tokens visible in group storage separately (read-only, not for claims) so the panel can show e.g. *"Head token — in group storage (unavailable)"* with a distinct icon/state. This helps teams confirm a transfer is in progress without accidentally thinking the slot is still claimed.

If a player had a slot claim and deposits the **only** copy of that token into group storage, their claim should drop on the next refresh — equipping that slot becomes illegal (red highlights + penalty overlay if still worn).

#### Bank-not-open behavior

When the personal bank container is unavailable, keep the **last known bank claim snapshot** and still refresh inventory every tick. When the player opens the bank, immediately force a full bank rescan.

### Distributing tokens in-game (manual, out of plugin scope)

Players trade slot tokens via group storage, direct trade, or drops. A common flow: deposit token into **group storage** (claim suspended) → teammate withdraws to **personal bank/inventory** (claim active). The plugin continuously reads personal inventory and bank — no manual refresh required.

### Implementation tasks

- [x] **STATE-3** — Implement `SlotStateService.refreshClaims()` (inventory + bank only; skip group storage + worn)
- [x] **STATE-4** — Implement `countHeldTokens()` and `heldTokenCount` tracking
- [x] **STATE-5** — Implement `refreshInventoryAndWorn()` on every `GameTick`
- [x] **STATE-6** — Implement `refreshBankIfAvailable()` every `bankRefreshInterval` ticks + on bank open
- [x] **STATE-7** — Subscribe `ItemContainerChanged` for INVENTORY, BANK, WORN
- [x] **STATE-8** — Subscribe `GameStateChanged` → full refresh on `LOGGED_IN`
- [x] **STATE-9** — Change-detection: only fire listeners when claim/equip masks change
- [x] **STATE-10** — (Optional) Read-only scan of `GROUP_STORAGE` for "in transit" panel state

---

## 5. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                   GroupSlotLockedPlugin                     │
│  - lifecycle, event subscriptions                         │
└────────────────────────────┬────────────────────────────────┘
                             │
                   ┌─────────▼─────────┐
                   │  SlotStateService │
                   │  - worn slots     │
                   │  - token claims   │
                   │  - held token cnt │
                   └─────────┬─────────┘
                             │
         ┌───────────────────┼───────────────────┐
         │                   │                   │
┌────────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
│ SlotValidator   │ │ SlotMenuHandler │ │ Overlays        │
│                 │ │                 │ │ - red highlights│
│                 │ │                 │ │ - black screen  │
└────────┬────────┘ └─────────────────┘ └────────┬────────┘
         │                                        │
         └────────────────┬───────────────────────┘
                          │
                ┌─────────▼─────────┐
                │ SlotPanel         │
                │ (sidebar UI)      │
                └───────────────────┘
```

### Core services

| Class | Responsibility |
|-------|----------------|
| `SlotStateService` | Local worn slots, token claims (personal bank + inventory only; **excludes group storage**), held token count. Refreshes on events **and** every game tick (see [§4](#detecting-token-ownership)). |
| `SlotValidator` | Pure functions: given local state + hypothetical equip, return `ValidationResult`. |
| `SlotMenuHandler` | Token examine labels + Wear deprioritization. |
| `GroupSlotLockedPanel` | Sidebar: which slots you can use. |
| `GroupSlotLockedPlugin` | Wires events → services → overlays → panel |

### Implementation tasks

- [x] **CFG-3** — `GroupSlotLockedPlugin.startUp()`: inject services, subscribe events, add overlays + panel
- [x] **CFG-4** — `GroupSlotLockedPlugin.shutDown()`: remove overlays, panel, cancel listeners

---

## 6. Slot Availability UI

Single **local-player** sidebar panel (`GroupSlotLockedPanel`). No teammate rows, no party data.

### Purpose

Answer at a glance:
- Which of the 11 slots can I equip right now?
- How many tokens am I holding (max 5)?
- How many slots am I wearing (max 5)?

### Layout

```
┌─ Group Slot Locked ─────────────┐
│ Tokens: 4/5    Equipped: 3/5    │
├─────────────────────────────────┤
│ [H] [C] [N] [A] [B] [L]         │  ← icon + display name per slot (see §9)
│ [MH][OH][Bo][Ri][Gl]            │
├─────────────────────────────────┤
│ ⚠ 6 tokens held — max is 5      │  ← warning when heldTokenCount > 5
└─────────────────────────────────┘
```

Each cell shows a **custom icon** and **display name** from [§9](#9-custom-slot-icons--display-names) (defaults: Head, Cape, Main hand, …).

Hover tooltip: `{displayName} — {state}` e.g. `Head — Available`.

### Per-slot cell states

| State | Condition | Visual |
|-------|-----------|--------|
| **Available** | Token in bank/inv for this slot AND `heldTokenCount ≤ 5` | Green border / tint |
| **Equipped** | Slot currently worn (non-empty) | Filled + checkmark or item icon |
| **No token** | Token not in personal bank/inv | Gray / dimmed |
| **Over token cap** | Token present but `heldTokenCount > 5` | Orange warning — token exists but claim inactive |
| **In group storage** | Token only in `GROUP_STORAGE` (optional read-only scan) | Striped / "in transit" — not usable |

Only one state applies per cell; priority: Equipped > Over cap > Available > In group storage > No token.

### Summary counters

```java
// Header labels — refresh on every SlotStateService change
"Tokens: " + heldTokenCount + "/" + config.maxHeldTokens();   // default 5
"Equipped: " + equippedCount + "/" + config.maxEquipped();    // default 5
```

When `heldTokenCount > maxHeldTokens`, show warning text and treat **all** slot claims as invalid until count drops (see `TOO_MANY_TOKENS` in [§7](#7-validation-logic)).

### Implementation notes

- Extend `PluginPanel`; register via `ClientToolbar` `NavigationButton` in `startUp()`.
- Subscribe panel to state changes via `SlotStateService` listener or invalidate on `GameTick` when mask changes.
- Use `ItemManager.getImage()` for equipped item thumbnail inside a slot cell (optional).
- Panel is the **only** required UI — no world overlays for team status, no party panel integration.

### Implementation tasks

- [x] **UI-1** — Create `GroupSlotLockedPanel` extending `PluginPanel`
- [x] **UI-2** — Register `NavigationButton` + sidebar icon in plugin `startUp()` / remove in `shutDown()`
- [x] **UI-3** — Render 11-slot grid with per-cell states (Available / Equipped / No token / Over cap / In transit)
- [x] **UI-4** — Header counters: `Tokens: x/5`, `Equipped: x/5`
- [x] **UI-5** — Warning banner when `heldTokenCount > maxHeldTokens`
- [x] **UI-6** — Wire panel to `SlotStateService` listener (refresh on mask change, not every frame repaint if unchanged)
- [x] **UI-7** — (Optional) Equipped item thumbnail in cell via `ItemManager.getImage()`
- [x] **UI-8** — Render `SlotDisplayService.getDisplayName(slot)` under each icon; tooltip on hover

### Config

```java
@ConfigItem(keyName = "showSlotPanel", ...)
default boolean showSlotPanel() { return true; }
```

---

## 7. Validation Logic

Central enum for violation reasons (used by overlays and logging):

```java
public enum Violation {
    NONE,
    NO_SLOT_CLAIM,        // missing token in personal bank/inventory for this slot
    TOO_MANY_TOKENS,      // >5 token items in bank+inventory — all claims suspended
    OVER_EQUIP_LIMIT,     // would exceed 5 equipped
    CURRENTLY_ILLEGAL     // already wearing something illegal
}
```

### Token counting

```java
int countHeldTokens() {
    int count = 0;
    for (Item item : union(inventoryItems, bankItems)) {
        if (SlotType.isTokenItem(item.getId())) count++;
    }
    return count;  // must be <= config.maxHeldTokens() (default 5) for claims to activate
}

boolean hasActiveClaim(SlotType slot) {
    return heldTokenCount <= config.maxHeldTokens()
        && tokenPresentInPersonalBankOrInv(slot);
}
```

### `canEquipItem(int itemId)` — preventive check

Inputs: item ID, local worn, local claims, `heldTokenCount`.

1. If `heldTokenCount > maxHeldTokens()` → `TOO_MANY_TOKENS` for any equip requiring a claim.
2. Resolve which `SlotType`(s) the item occupies.
3. For each slot: if `!hasActiveClaim(slot)` → `NO_SLOT_CLAIM`.
4. Count currently equipped tracked slots + new slots − slots that would be replaced by the new item.
5. If count > 5 → `OVER_EQUIP_LIMIT`.
6. Else → `NONE`.

### `getCurrentViolations()` — reactive check

Run after every worn container change. Return list of all active violations on current loadout (may be multiple).

### Two-handed / slot replacement edge cases

- Equipping a 2H weapon clears off-hand — recalculate counts after simulated equip.
- Some items occupy unexpected slots (e.g. salamander) — always use `ItemComposition` wear positions, not item name heuristics.

### Implementation tasks

- [x] **VAL-1** — Create `Violation` enum and `ValidationResult` model
- [x] **VAL-2** — Implement `hasActiveClaim(SlotType)` (token present + held count ≤ max)
- [x] **VAL-3** — Implement `canEquipItem(int itemId)` preventive check
- [x] **VAL-4** — Implement `getCurrentViolations()` reactive check on worn loadout
- [x] **VAL-5** — Implement 2H / slot-replacement simulation in equip count
- [x] **VAL-6** — Unit tests in `SlotValidatorTest` (no client required): no tokens, 6 tokens, valid claim, over equip, 2H weapon

---

## 8. Visual Feedback

### A. Red item highlight (`ItemRestrictionOverlay`)

Extend `WidgetItemOverlay`:

- `showOnInventory()` and `showOnEquipment()`.
- In `renderItemOverlay`: if `SlotValidator.getViolationForItem(itemId) != NONE`, draw semi-transparent red rectangle + optional "✕" or slot icon.

Reference implementations: Inventory Tags plugin, Bank Tags `WidgetItemOverlay` usage.

#### Implementation tasks

- [x] **OVR-1** — Create `ItemRestrictionOverlay` extending `WidgetItemOverlay`
- [x] **OVR-2** — Highlight red when `getViolationForItem(itemId) != NONE`
- [x] **OVR-3** — Register overlay in `startUp()`; respect `highlightRestricted` config

### B. Black screen penalty (`ViolationOverlay`)

Extend `Overlay` with `OverlayLayer.ABOVE_SCENE` (or appropriate layer above game world but below widgets):

- When `getCurrentViolations()` is non-empty for **currently worn** gear:
  - Fill entire canvas with opaque black **except** cut out regions matching:
    - Inventory widget bounds
    - Equipment (worn items) widget bounds
    - Optionally: bank if open
- Use `client.getWidget(InterfaceID.Inventory.ITEMS)` and equipment panel widgets from gameval.
- Show centered text: `"Illegal loadout — unequip restricted gear"`.

Config toggle: enable/disable penalty overlay.

#### Implementation tasks

- [x] **OVR-4** — Create `ViolationOverlay` with black fill + inventory/equipment cutouts
- [x] **OVR-5** — Show violation message text when active
- [x] **OVR-6** — Respect `penaltyOverlay` config

### C. Chat warnings (optional, low frequency)

On transition into illegal state (not every frame):

```java
client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Group Slot Locked: 6/5 tokens held — store one in group storage.", null);
```

Use `log.debug` for diagnostics; chat only on state **edge** changes.

#### Implementation tasks

- [x] **OVR-7** — Chat message on transition into illegal state (edge-triggered, not per-tick)

### D. Static left-click / menu deprioritization

Prevent accidental equips by changing which menu option is the **default left-click** (single-mouse-button mode) and top of the right-click menu. Use **deprioritization and reordering only** — do not remove menu entries.

Reference: core **Menu Entry Swapper** plugin patterns (`MenuEntryAdded`, `MenuOpened`, `MenuEntry.setDeprioritized()`).

#### Two tiers

| Tier | Items | Behavior | Config |
|------|-------|----------|--------|
| **Static (token items)** | Team capes 6, 16, 26, 36, 46, 10, 20, 30, 40, 50, 7 (`SlotType.tokenItemId()`) | **Always** deprioritize Wear/Wield; promote **Examine** as left-click with slot-specific menu text | On by default |
| **Dynamic (illegal gear)** | Any item where `canEquipItem(itemId) != NONE` | Deprioritize Wear/Wield/Wear while rules block equipping | On by default |

Token items are claim markers — players should not accidentally **wear** them. Illegal gear covers everything else the validator rejects (no claim, slot taken, over 5 limit).

#### Implementation: `SlotMenuHandler`

```java
public class SlotMenuHandler {
    private static final Set<String> EQUIP_OPTIONS = Set.of("wear", "wield", "equip");

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!isItemContainerWidget(event)) return;

        int itemId = event.getItemId();
        MenuEntry entry = event.getMenuEntry();

        if (SlotType.isTokenItem(itemId)) {
            SlotType slot = SlotType.fromTokenItemId(itemId);
            if (isEquipOption(entry.getOption())) {
                entry.setDeprioritized(true);
            } else if (isExamineOption(entry.getOption())) {
                applyTokenSlotMenuText(entry, slot);
            }
            return;
        }

        if (config.deprioritizeIllegalEquips()
            && isEquipOption(entry.getOption())
            && slotValidator.getViolationForItem(itemId) != Violation.NONE) {
            entry.setDeprioritized(true);
        }
    }

    @Subscribe
    public void onMenuOpened(MenuOpened event) {
        // Final pass — inventory Wear/Wield ops are often built before MenuEntryAdded.
        // Reorder entries so preferred left-click is on top (array is reversed: last = top).
        MenuEntry[] entries = event.getMenuEntries();
        // ... swap logic mirroring Menu Entry Swapper ...
    }
}
```

**Widget scope:** Only apply when the menu target is in **inventory**, **personal bank**, **equipment panel**, or **group storage** (deprioritize wear on tokens in shared storage too — still don't want to wear them). Use gameval `InterfaceID` checks on `entry.getWidget()`.

**Static token left-click default: Examine**

Default left-click for all 11 token items is **Examine**, not Wear. The menu text must identify **which slot** the token grants so players do not confuse Team-6 vs Team-20 capes.

```java
public enum TokenLeftClick {
    EXAMINE,  // default — safe, informative
    DROP,
    USE
}
```

Add display helpers via `SlotDisplayService` (not hardcoded only on enum):

```java
// SlotDisplayService — default names built-in, user overrides optional
displayService.getDisplayName(SlotType.HEAD);     // "Head" or custom "Tank helm"
displayService.getClaimLabel(SlotType.HEAD);      // "Head slot claim"
displayService.getExamineOptionText(SlotType.HEAD); // "Examine Head slot"
displayService.getPanelAbbrev(SlotType.HEAD);       // "H" — used if name too long
```

#### Slot-specific menu text (token items only)

When building the menu for a token item, rewrite the **Examine** entry so the label references the slot. Keep the underlying `MenuAction` as the normal examine/CC_OP action — only change display text via `setOption()` / `setTarget()`.

```java
private void applyTokenSlotMenuText(MenuEntry entry, SlotType slot) {
    entry.setOption(displayService.getExamineOptionText(slot));
    entry.setTarget(entry.getTarget() + " — " + displayService.getClaimLabel(slot));
}
```

| Token | `examineOptionText()` | `claimLabel()` |
|-------|----------------------|----------------|
| Team-6 cape | Examine Head slot | Head slot claim |
| Team-16 cape | Examine Cape slot | Cape slot claim |
| Team-20 cape | Examine Main hand slot | Main hand slot claim |
| … | … | … |

**Optional examine chat message:** On `MenuOptionClicked` for token examine, queue a short plugin chat line so the slot is obvious even without reading the menu:

```java
chatMessageManager.queue(QueuedMessage.builder()
    .type(ChatMessageType.GAMEMESSAGE)
    .value("Group Slot Locked: Team-6 cape grants the <col=ff9040>Head</col> equipment slot.")
    .build());
```

Use the item's real examine text from the game as well (do not replace it) — append the plugin line after, or only show plugin line if config `tokenExamineHint` is on.

For token items, deprioritize all equip options and ensure **Examine** (with slot label) sorts to the top so left-click examines instead of wears.

#### MenuOpened reorder pattern

`MenuOpened.getMenuEntries()` returns entries in **reverse display order** (last element = top / left-click default). To promote Examine above Wear:

1. Find indices of the slot-labelled Examine entry and Wear entry for the same item.
2. Swap or move Examine to the higher index (top of menu).
3. Call `event.setMenuEntries(reordered)`.
4. Re-apply `applyTokenSlotMenuText()` on the examine entry in this pass (MenuOpened may clone entries).

If reorder alone is insufficient, set `entry.setDeprioritized(true)` on all equip options.

#### Safety net: click consumption

If equip still fires (lag, edge case, menu swapper conflict), optionally block in `MenuOptionClicked`:

```java
@Subscribe
public void onMenuOptionClicked(MenuOptionClicked event) {
    if (!config.blockIllegalEquips() || !isEquipAction(event)) return;
    int itemId = extractItemId(event);
    if (SlotType.isTokenItem(itemId) || slotValidator.getViolationForItem(itemId) != Violation.NONE) {
        event.consume();
    }
}
```

Ship `blockIllegalEquips` default **off** if deprioritization works reliably; enable during playtesting if needed.

#### Hub / AGENTS.md notes

- **Allowed:** Deprioritize/swap menu order on specific item IDs and on items failing plugin validation (similar to [Color Lock](https://github.com/unidarkshin/osrs-color-lock-runelite) restricting equip actions).
- **Not allowed:** Removing menu entries entirely; adding new server actions; resizing click zones.
- **Static token list** is hub-friendly because it is a fixed, documented set of 11 IDs — not PvP-targeted conditional removal.

#### Implementation tasks

- [x] **MENU-1** — Create `SlotMenuHandler` with widget scope checks (inv, bank, equipment, group storage)
- [x] **MENU-2** — Deprioritize Wear/Wield on all 11 token item IDs
- [x] **MENU-3** — Apply slot-specific Examine option/target text on token items
- [x] **MENU-4** — `MenuOpened` reorder pass: promote Examine above Wear
- [x] **MENU-5** — Optional `tokenExamineHint` chat on token examine
- [x] **MENU-6** — Deprioritize Wear on illegal gear when `deprioritizeIllegalEquips` enabled
- [x] **MENU-7** — Optional `MenuOptionClicked.consume()` when `blockIllegalEquips` enabled

*(Manual verification: [§14 — Menu tests](#menu--left-click-tests))*

---

## 9. Custom Slot Icons & Display Names

Each slot has a **default display name** and optional **user override**. Names appear in the sidebar panel, examine menu text, chat hints, and tooltips. Icons (separate from names) are covered below.

### Default display names (built-in)

Ship these defaults — teams can rename via overrides without code changes:

| `SlotType` | Default display name | Default panel abbrev | Token |
|------------|---------------------|----------------------|-------|
| `HEAD` | Head | H | Team-6 cape |
| `CAPE` | Cape | C | Team-16 cape |
| `NECK` | Neck | N | Team-26 cape |
| `AMMO` | Ammo | A | Team-36 cape |
| `BODY` | Body | B | Team-46 cape |
| `LEGS` | Legs | L | Team-10 cape |
| `MAIN_HAND` | Main hand | MH | Team-20 cape |
| `OFF_HAND` | Off hand | OH | Team-30 cape |
| `BOOTS` | Boots | Bo | Team-40 cape |
| `RING` | Ring | Ri | Team-50 cape |
| `GLOVES` | Gloves | Gl | Team-7 cape |

Derived strings (use display name, not enum name):

```java
getClaimLabel(slot)         → "{displayName} slot claim"     // "Head slot claim"
getExamineOptionText(slot)  → "Examine {displayName} slot"   // "Examine Head slot"
```

### Custom name overrides

Same pattern as icons — store under `.runelite/group-slot-locked/`:

**Option A — JSON file (recommended):**

```
~/.runelite/group-slot-locked/slot-names.json
```

```json
{
  "head": "Tank helm",
  "main_hand": "DPS weapon",
  "off_hand": "Off-hand / shield"
}
```

Keys match slot filenames (`head`, `main_hand`, …). Missing keys fall back to built-in defaults.

**Option B — config string** (quick override for one slot):

```java
@ConfigItem(keyName = "customSlotNames", ...)
default String customSlotNames() { return ""; }
// Format: head=Tank helm,main_hand=DPS weapon  (comma-separated, optional)
```

Resolution order: config string → `slot-names.json` → bundled defaults.

### `SlotDisplayService`

Single service for **names + icons** (or split if preferred; one class keeps panel code simple):

```java
public class SlotDisplayService {
    String getDisplayName(SlotType slot);
    String getPanelAbbrev(SlotType slot);      // abbrev if custom name > N chars
    String getClaimLabel(SlotType slot);
    String getExamineOptionText(SlotType slot);
    BufferedImage getIcon(SlotType slot);      // icon tier: override → bundled → item sprite
    void reloadNames();
    void reloadIcons();
}
```

### Implementation tasks — display names

- [x] **NAME-1** — Default name map for all 11 `SlotType` values (table above)
- [x] **NAME-2** — Load optional `slot-names.json` from `RuneLite.RUNELITE_DIR/group-slot-locked/`
- [x] **NAME-3** — Parse optional `customSlotNames` config override
- [x] **NAME-4** — Wire names into panel cells, tooltips, `SlotMenuHandler`, examine chat hint
- [x] **NAME-5** — Panel: show display name under icon (truncate with abbrev if > ~12 chars)

---

### Custom slot icons

Each of the 11 slot claim tokens (Team capes 6, 16, 26, 36, 46, 10, 20, 30, 40, 50, 7) can have a **custom icon** shown in the sidebar panel, overlays, and token-item highlights. Icons help players quickly recognize which cape grants which slot without memorizing numbers.

### Goals

- Replace generic slot labels with recognizable visuals (custom art, recolored cape sprites, emoji-style icons, etc.).
- Allow **per-slot overrides** without rebuilding the plugin.
- Ship **sensible defaults** so the plugin works out of the box.
- Keep memory reasonable — PNGs are loaded at full resolution (`width × height × 4` bytes).

### Icon resolution order

For each `SlotType`, resolve the display icon in this order:

1. **User override** — PNG in the plugin data directory (see below)
2. **Bundled default** — PNG shipped in `src/main/resources/icons/slots/`
3. **Token item sprite** — fallback via `ItemManager.getImage(tokenItemId)` (actual Team cape sprite from the game)

```java
BufferedImage icon = slotDisplayService.getIcon(SlotType.HEAD);
String label = slotDisplayService.getDisplayName(SlotType.HEAD);
```

### File naming & locations

| Slot | Token | Override filename | Bundled resource |
|------|-------|-------------------|------------------|
| HEAD | Team-6 cape | `head.png` | `icons/slots/head.png` |
| CAPE | Team-16 cape | `cape.png` | `icons/slots/cape.png` |
| NECK | Team-26 cape | `neck.png` | `icons/slots/neck.png` |
| AMMO | Team-36 cape | `ammo.png` | `icons/slots/ammo.png` |
| BODY | Team-46 cape | `body.png` | `icons/slots/body.png` |
| LEGS | Team-10 cape | `legs.png` | `icons/slots/legs.png` |
| MAIN_HAND | Team-20 cape | `main_hand.png` | `icons/slots/main_hand.png` |
| OFF_HAND | Team-30 cape | `off_hand.png` | `icons/slots/off_hand.png` |
| BOOTS | Team-40 cape | `boots.png` | `icons/slots/boots.png` |
| RING | Team-50 cape | `ring.png` | `icons/slots/ring.png` |
| GLOVES | Team-7 cape | `gloves.png` | `icons/slots/gloves.png` |

**User override directory** (per AGENTS.md — only write inside `.runelite`):

```
~/.runelite/group-slot-locked/icons/
├── head.png
├── cape.png
├── ...
└── gloves.png
```

In code:

```java
private static final Path ICON_DIR = RuneLite.RUNELITE_DIR
    .toPath()
    .resolve("group-slot-locked")
    .resolve("icons");
```

Also provide a **`manifest.json`** (optional) in the same folder for teams sharing a full icon pack:

```json
{
  "version": 1,
  "slots": {
    "head": "head.png",
    "cape": "cape.png"
  }
}
```

If present, validate that referenced files exist; ignore unknown keys.

### `SlotDisplayService` — icon methods

Icon loading (can live on same class as names):

```java
BufferedImage getIcon(SlotType slot);       // never null
boolean hasCustomIcon(SlotType slot);
void importIcon(SlotType slot, Path sourceFile);
void resetIcon(SlotType slot);
void reloadIcons();
void openIconFolder();
```

- Load bundled icons with `ImageUtil.loadImageResource(GroupSlotLockedPlugin.class, "icons/slots/" + slot.fileName())`.
- Load overrides with `ImageIO.read(path)` on an executor thread; cache results in `EnumMap<SlotType, BufferedImage>`.
- Validate imports: **PNG only**, max dimension e.g. **64×64** (scale down if larger to save memory).
- On invalid/corrupt file, log at `debug` and fall back to next tier.

### How users upload icons

Support **three paths** (implement at least 1 and 2):

#### A. Sidebar panel import (recommended UX)

In `GroupSlotLockedPanel`, each slot cell gets a right-click or ⚙ context menu:

| Action | Behavior |
|--------|----------|
| **Edit name…** | Text input or prompt; save to `slot-names.json`; reload |
| **Import icon…** | `JFileChooser` filtered to `.png`; copy selected file → `ICON_DIR/{slot}.png`; reload |
| **Reset to default** | Delete override file; reload |
| **Open icons folder** | Reveal `~/.runelite/group-slot-locked/icons/` so users can drag-drop multiple files |

`JFileChooser` is explicitly allowed in AGENTS.md for user-initiated file ops. Perform copy + `ImageIO` on OkHttp executor or a small single-thread executor — **not** on the client thread.

#### B. Manual file drop

Document in README:

1. Create folder `%USERPROFILE%\.runelite\group-slot-locked\icons\` (Windows) or `~/.runelite/group-slot-locked/icons/` (macOS/Linux).
2. Add PNGs named per the table above.
3. In plugin panel, click **Reload icons** (or restart plugin).

#### C. Shareable icon pack (optional, Phase 4)

- **Export pack** — zip `icons/` folder + `manifest.json` for sharing with teammates.
- **Import pack** — `JFileChooser` on a `.zip`; extract into `ICON_DIR` (validate paths, no zip-slip).

Teammates can share icon packs out of band — icons are local only.

### Where icons appear

| Surface | Usage |
|---------|--------|
| **Slot availability panel** | Primary — 11-icon grid for local player (see [§6](#6-slot-availability-ui)) |
| **Token item overlay** | Small badge on Team capes in inventory/bank showing which slot they claim |
| **Violation chat** | Optional inline icon in `"Head slot taken by …"` messages |
| **Config preview** | Thumbnail row showing current icon set |

Apply status tinting **on top of** the icon (green/red/yellow border or overlay), not by recoloring the PNG itself.

### Bundled default art

Ship lightweight defaults in repo (optimize PNGs before commit):

- Option A: Simple monochrome slot silhouettes (helmet, cape, amulet, etc.)
- Option B: Downscaled team cape colors (6, 16, 26, 36, 46, 10, 20, 30, 40, 50, 7) with slot label
- Option C: No bundled files — rely on `ItemManager` token sprite until user imports

Recommend **Option A or B** so the panel looks intentional before customization.

Example resource layout:

```
src/main/resources/icons/slots/
├── head.png
├── cape.png
└── ... (11 files, ≤32×32 each)
```

### Config hooks

```java
@ConfigItem(keyName = "useCustomSlotIcons", ...)
default boolean useCustomSlotIcons() { return true; }

@ConfigItem(keyName = "showTokenBadge", ...)
default boolean showTokenBadge() { return true; }  // badge on cape items in inv/bank
```

When `useCustomSlotIcons` is false, skip override lookup and use bundled → token sprite only.

### Implementation tasks — icons

- [x] **ICON-1** — Icon tier fallback in `SlotDisplayService` (override → bundled → item sprite)
- [x] **ICON-2** — Add bundled `icons/slots/*.png` defaults (11 files, optimized)
- [x] **ICON-3** — Load/save overrides under `~/.runelite/group-slot-locked/icons/`
- [x] **ICON-4** — Panel: use `getIcon(SlotType)` in slot grid cells
- [x] **ICON-5** — (Optional) Token badge overlay on capes in inv/bank
- [x] **ICON-6** — Panel: Import / Reset / Reload / Open folder via `JFileChooser`
- [ ] **ICON-7** — (Optional) Zip pack import/export

*(Manual verification: [§14 — Icon tests](#icon-tests))*

---

## 10. Config

Rename config group from `"example"` → `"group-slot-locked"`.

```java
@ConfigGroup("group-slot-locked")
public interface GroupSlotLockedConfig extends Config {
    @ConfigItem(keyName = "penaltyOverlay", ...)
    default boolean penaltyOverlay() { return true; }

    @ConfigItem(keyName = "highlightRestricted", ...)
    default boolean highlightRestricted() { return true; }

    @ConfigItem(keyName = "showSlotPanel", ...)
    default boolean showSlotPanel() { return true; }

    @ConfigItem(keyName = "maxHeldTokens", ...)
    default int maxHeldTokens() { return 5; }

    @ConfigItem(keyName = "maxEquipped", ...)
    default int maxEquipped() { return 5; }

    @ConfigItem(keyName = "deprioritizeTokenWear", ...)
    default boolean deprioritizeTokenWear() { return true; }  // static: Team capes 6/16/26/36/46/10/20/30/40/50/7

    @ConfigItem(keyName = "tokenLeftClick", ...)
    default TokenLeftClick tokenLeftClick() { return TokenLeftClick.EXAMINE; }

    @ConfigItem(keyName = "tokenExamineHint", ...)
    default boolean tokenExamineHint() { return true; }  // chat hint naming the slot on token examine

    @ConfigItem(keyName = "deprioritizeIllegalEquips", ...)
    default boolean deprioritizeIllegalEquips() { return true; }  // dynamic: validator failures

    @ConfigItem(keyName = "blockIllegalEquips", ...)
    default boolean blockIllegalEquips() { return false; }  // consume equip click; backup only

    @ConfigItem(keyName = "useCustomSlotIcons", ...)
    default boolean useCustomSlotIcons() { return true; }

    @ConfigItem(keyName = "showTokenBadge", ...)
    default boolean showTokenBadge() { return true; }

    @ConfigItem(keyName = "bankRefreshInterval", ...)
    default int bankRefreshInterval() { return 10; }  // game ticks between bank rescans when loaded

    @ConfigItem(keyName = "customSlotNames", ...)
    default String customSlotNames() { return ""; }  // optional inline overrides: head=Tank helm,...

    @ConfigItem(keyName = "showSlotNameLabels", ...)
    default boolean showSlotNameLabels() { return true; }  // display name under icon in panel

    @ConfigItem(keyName = "customTokens", ...)
    default String customTokens() { return ""; }  // future: override token IDs per slot
}
```

### Implementation tasks

- [x] **CFG-1** — Replace template config group `"example"` → `"group-slot-locked"` with all keys above
- [x] **CFG-2** — Wire every `@ConfigItem` to the feature that reads it (no dead config keys)

---

## 11. Suggested File Structure

```
src/main/java/com/gsl/
├── GroupSlotLockedPlugin.java       # Main plugin entry
├── GroupSlotLockedConfig.java       # Config interface
├── model/
│   ├── SlotType.java                # Enum + equipment/token mapping
│   ├── LocalSlotState.java          # Worn mask, claim mask, held token count
│   └── ValidationResult.java        # Violation + message
├── service/
│   ├── SlotStateService.java        # Local state tracking
│   ├── SlotValidator.java           # Pure validation logic
│   └── SlotDisplayService.java      # Slot display names + icon load/cache/import
├── menu/
│   └── SlotMenuHandler.java         # Static token + illegal equip menu deprioritization
├── overlay/
│   ├── ItemRestrictionOverlay.java  # Red highlights
│   └── ViolationOverlay.java        # Black screen
└── ui/
    └── GroupSlotLockedPanel.java    # Local slot availability sidebar

src/main/resources/
├── panel_icon.png                   # Plugin hub icon (optimize PNG size)
└── icons/slots/                     # Bundled default slot icons (11 PNGs)
    ├── head.png
    ├── cape.png
    └── ...
```

---

## 12. Implementation Phases

Master checklist — detail lives in section task lists above.

### Phase 0 — Skeleton cleanup
See [§13](#13-skeleton-cleanup-do-first). All `P0-*` must be `[x]` before feature work.

### Phase 1 — Core logic + overlays
- [x] **PH1-1** — All `STATE-*` tasks complete
- [x] **PH1-2** — All `VAL-*` tasks complete (+ unit tests green)
- [x] **PH1-3** — All `MENU-*` tasks complete
- [x] **PH1-4** — All `OVR-1` through `OVR-7` complete
- [x] **PH1-5** — All `CFG-*` tasks complete
- [x] **PH1-6** — Plugin `startUp`/`shutDown` registers services, events, overlays

**Done when:** [§14 solo tests](#solo-tests) T1–T8 pass.

### Phase 2 — Slot availability panel + icons + names
- [x] **PH2-1** — All `UI-*` tasks complete
- [x] **PH2-2** — All `NAME-*` tasks complete
- [x] **PH2-3** — All `ICON-1` through `ICON-5` complete
- [x] **PH2-4** — `OVR-7` chat warnings wired

**Done when:** [§14 panel + group storage tests](#group-storage-tests) pass.

### Phase 3 — Polish & hub prep
- [ ] **POL-1** — `ICON-6`, `ICON-7` (optional import/export)
- [ ] **POL-2** — Remove `com.example.*` template code
- [ ] **POL-3** — README: tokens, max 5 held, group storage transfers, testing steps
- [ ] **POL-4** — Plugin descriptor description + tags finalized
- [ ] **POL-5** — Performance review: no full-scene scans; party send N/A
- [ ] **POL-6** — User confirms in-game golden path (see [§14 sign-off](#sign-off-before-hub--done))

---

## 13. Skeleton Cleanup (Do First)

The repo still has template remnants. Fix before feature work:

- [x] **P0-1** — `runelite-plugin.properties`: set `plugins=com.gsl.GroupSlotLockedPlugin`
- [x] **P0-2** — `GroupSlotLockedConfig.java`: config group `"group-slot-locked"`, remove greeting placeholder
- [x] **P0-3** — `GroupSlotLockedPlugin.java`: remove example login chat; no party dependency
- [x] **P0-4** — `build.gradle`: `group = 'com.gsl'`, update `pluginMainClass` to launch this plugin
- [x] **P0-5** — `settings.gradle`: confirm project name matches repo
- [x] **P0-6** — Delete or repurpose `com.example.*` if unused
- [ ] **P0-7** — Verify `./gradlew run` starts RuneLite with plugin loaded (developer mode)

Add to plugin descriptor:

```java
@PluginDescriptor(
    name = "Group Slot Locked",
    description = "Slot token claims and equip limits for group ironman",
    tags = {"ironman", "group", "equipment"},
    enabledByDefault = false
)
public class GroupSlotLockedPlugin extends Plugin { ... }
```

---

## 14. Testing — How to Verify

Per `AGENTS.md`: **agents cannot play the game for you.** Automated tests cover pure logic; **you** (or a human tester) must confirm visuals and menus in the dev client.

### Test layers

| Layer | Command / tool | What it covers | Who runs it |
|-------|----------------|----------------|-------------|
| **Unit** | `./gradlew test` | `SlotValidator`, token counting, 2H slot math, `SlotType` mapping | Any agent / CI |
| **Dev client** | `./gradlew run` | Overlays, panel, menus, bank refresh, group storage | Human on test account |
| **Sign-off** | Same dev client | Full golden path on GIM or alt with team capes | Plugin author |

### Setup — dev client

1. From repo root: `./gradlew run` (Windows: `gradlew.bat run`).
2. Log in per [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) (Jagex account section).
3. Enable **Group Slot Locked** in RuneLite plugin panel (wrench → search "Group Slot Locked").
4. Open sidebar panel (plugin navigation button) so slot grid is visible.

### Setup — test items

Team capes 6, 16, 26, 36, 46, 10, 20, 30, 40, 50, and 7 are **tradable** — obtain via:

- **Grand Exchange** on a non-restricted account, trade to test character, **or**
- **Group ironman:** buy from other players / team stockpile, **or**
- **Developer / admin spawn** if your test environment supports it (not required for normal users).

Also need generic gear for equip tests: any helm (head), body, legs, etc.

**Suggested test loadout in bank:**

| Item | Purpose |
|------|---------|
| Team-6, Team-26, Team-46, Team-20, Team-50 capes | 5 tokens (at cap) |
| Team-16 cape | 6th token (over-cap test) |
| Bronze med helm / any head slot item | Equip test without head token |
| Any 2H sword | Main + off hand count test |

### Setup — group storage tests

Requires a **group ironman** character with access to shared storage (or two teammates coordinating). Non-GIM accounts can skip [group storage tests](#group-storage-tests) and mark them `skipped — no GIM` in PR notes.

### Unit tests (implement in Phase 1)

Create `src/test/java/com/gsl/SlotValidatorTest.java`:

```java
// Examples — no Client mock needed if validator is pure:
// - heldTokenCount=6 → hasActiveClaim(any) false, canEquipItem → TOO_MANY_TOKENS
// - heldTokenCount=3, head token present → canEquipItem(helm) → NONE
// - no head token → canEquipItem(helm) → NO_SLOT_CLAIM
// - 5 slots filled, equipping 6th → OVER_EQUIP_LIMIT
// - 2H weapon → occupies MAIN_HAND + OFF_HAND in count
```

Run: `./gradlew test`

Mark **VAL-6** `[x]` only when these pass.

### Manual test procedure

For each checkbox below: perform steps → observe expected result → check box in this file when passing.

Enable **Developer Tools** (RuneLite settings) if you need widget bounds debugging for overlay cutouts.

---

### Solo tests

- [ ] **T1** — No tokens in bank/inv → panel all gray; equipping any gear shows red highlight
- [ ] **T2** — Hold 3 distinct tokens in inv → panel shows 3 green slots; header `Tokens: 3/5`
- [ ] **T3** — Pick up 6th token → header `Tokens: 6/5` warning; all claims suspended; gear red
- [ ] **T4** — Bank or drop one token → back to 5 → claims reactivate
- [ ] **T5** — With Team-20 in bank, main-hand slot green; can equip weapon without red highlight
- [ ] **T6** — Wear 5 gear pieces → `Equipped: 5/5`; 6th piece shows red
- [ ] **T7** — With illegal loadout worn → black penalty overlay; inventory/equipment still visible
- [ ] **T8** — Unequip violating item → penalty overlay clears immediately

<details>
<summary>T1 step-by-step</summary>

1. Empty bank/inv of all team capes.
2. Open sidebar panel → all 11 cells gray/dimmed.
3. Left-click wear any helm → red overlay on helm (if highlight enabled); penalty if already wearing illegal gear.

</details>

<details>
<summary>T3 step-by-step</summary>

1. Put 6 different team capes in inventory (Team-6, Team-16, Team-26, Team-36, Team-46, Team-10).
2. Panel header shows `Tokens: 6/5` and warning text.
3. Try equipping gear for a slot you hold a token for → still red (claims suspended).

</details>

---

### Group storage tests

- [ ] **G1** — Deposit only copy of head token (Team-6) to group storage → head cell gray; head gear red if worn
- [ ] **G2** — Withdraw Team-6 to personal bank → head cell green
- [ ] **G3** — Wear head gear while token only in group storage → penalty overlay active

<details>
<summary>G1 step-by-step</summary>

1. Single Team-6 cape in inventory.
2. Confirm head slot green in panel.
3. Open group storage → deposit Team-6.
4. Within ~1–2 ticks head slot gray; if still wearing helm, penalty overlay on.

</details>

---

### Edge cases

- [ ] **E1** — Equip 2H weapon → panel/`Equipped` counts main + off as 2 slots
- [ ] **E2** — Swap 2H for 1H + shield → equipped count updates correctly
- [ ] **E3** — Receive token via trade mid-session → panel updates without relog
- [ ] **E4** — Close bank with tokens inside → claims persist; inv-only refresh still works

---

### Menu / left-click tests

- [ ] **M1** — Left-click Team-6 cape → Examine (not Wear); option reads "Examine Head slot"
- [ ] **M2** — Team-20 cape → "Examine Main hand slot" / "Main hand slot claim" in target
- [ ] **M3** — Right-click any token → Wear below Examine
- [ ] **M4** — Examine token with `tokenExamineHint` on → chat names the slot
- [ ] **M5** — Helm without head claim → Wear deprioritized (not default left-click)
- [ ] **M6** — After adding head token → helm Wear priority restored
- [ ] **M7** — `deprioritizeTokenWear = false` → token capes behave vanilla

---

### Display name tests

- [ ] **N1** — Panel shows default names (Head, Main hand, …) under icons
- [ ] **N2** — Custom name via `slot-names.json` → panel + examine menu use new label
- [ ] **N3** — `showSlotNameLabels = false` → icons only, no text under cells

### Icon tests

- [ ] **I1** — Default icons show for all 11 panel cells
- [ ] **I2** — Import custom PNG via panel → updates after reload
- [ ] **I3** — Reset icon → bundled/token fallback
- [ ] **I4** — Token badge on capes in inventory when `showTokenBadge` enabled

---

### Sign-off before hub / "done"

Human tester confirms on a **real session** (not just login screen):

1. [ ] **S1** — Golden path: 5 tokens assigned → equip 5 gear slots → panel green → no penalty
2. [ ] **S2** — Transfer token via group storage → claim drops → re-grants after withdraw
3. [ ] **S3** — Plugin disable → all menus/overlays revert to vanilla
4. [ ] **S4** — `./gradlew test` green; `./gradlew build` succeeds

Record account type (GIM / normal iron / main) and client version in PR or README when signing off.

---

## 15. Open Questions

Decide with the team before or during implementation:

1. **Token item choice:** Team capes 6, 16, 26, 36, 46, 10, 20, 30, 40, 50, 2 — confirmed. Richard, Sam, and Ian are easy to trade without taking damage from enemies (good luck not getting PKed).
2. **Over token cap behavior:** When holding >5 tokens, suspend all claims (current plan) vs. allow oldest 5 only?
3. **Penalty severity:** Full black screen vs dimmed screen vs flashing border — start with black screen as specified.

---

## Appendix: Key API References

| API | Use |
|-----|-----|
| `EquipmentInventorySlot` | Worn container indices |
| `InventoryID.WORN`, `InventoryID.INVENTORY`, `InventoryID.BANK` | Claim/equipment containers (**use these for claims**) |
| `InventoryID.GROUP_STORAGE` | Shared GIM storage — **do not** use for claims; optional read-only UI for "token in transit" |
| `ItemContainerChanged` | Equipment/inventory/bank change events |
| `ItemComposition.getWearPos1/2/3()` | Which slots an item uses |
| `WidgetItemOverlay` | Red item highlights |
| `InterfaceID` (gameval) | Widget lookups for penalty cutouts |
| `ItemManager.getImage()` | Slot icons + equipped item thumbnails in panel |
| `MenuEntryAdded` / `MenuOpened` | Deprioritize/reorder Wear on tokens and illegal gear |
| `MenuEntry.setOption()` / `setTarget()` | Slot-specific Examine labels on token items |
| `MenuEntry.setDeprioritized()` | Move Wear below Examine for left-click safety |
| `MenuOptionClicked.consume()` | Optional backup block if equip still fires |
| `ItemManager.canonicalize()` | Normalize item IDs for token matching |
| `ImageUtil.loadImageResource()` | Load bundled PNG defaults |
| `JFileChooser` | User-initiated custom icon import |
| `RuneLite.RUNELITE_DIR` | Base path for user icon overrides |

## Appendix: Example Validation Pseudocode

```java
public ValidationResult validateEquip(int itemId, LocalSlotState local) {
    if (local.heldTokenCount() > config.maxHeldTokens()) {
        return fail(TOO_MANY_TOKENS, null);
    }
    Set<SlotType> needed = slotsForItem(itemId);
    Set<SlotType> afterEquip = simulateEquip(local.equippedSlots(), needed);

    for (SlotType slot : needed) {
        if (!local.hasActiveClaim(slot)) return fail(NO_SLOT_CLAIM, slot);
    }
    if (afterEquip.size() > config.maxEquipped()) return fail(OVER_EQUIP_LIMIT, null);
    return ok();
}
```

---

*Last updated: 2026-07-09 — added multi-agent checkboxes + testing guide*
