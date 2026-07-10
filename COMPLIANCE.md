# Group Slot Locked — Compliance Review

Reference document for RuneLite Plugin Hub and Jagex third-party client guideline compliance.

**Last reviewed:** July 2026  
**Sources:**
- [Jagex Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
- [RuneLite Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- Project `AGENTS.md` plugin rules

---

## Plugin Summary

Group Slot Locked is a **voluntary Group Ironman challenge helper**. Players use Wilderness Cape items as per-slot "claim tokens" and self-impose equip limits (max tokens held, max slots filled, slot claims).

The plugin provides:

- Slot token tracking and equip validation
- Sidebar panel with slot availability
- Visual feedback (red highlights, penalty overlay, custom token icons)
- Menu relabeling and optional left-click reordering for tokens
- Bank search by custom slot names
- Custom slot names and icons (local files only)
- Optional chat warnings and examine hints

**Not present:** HTTP/network, combat/PvP/boss logic, input injection, outgoing chat modification, reflection on game classes.

**Plugin descriptor:** `enabledByDefault = false` — user must enable in the plugin list.

---

## Clearly Compliant

### Jagex guidelines

| Area | Status | Notes |
|------|--------|-------|
| Automation / botting | OK | No input injection; optional click consume is user-initiated |
| Combat assistance | OK | No combat features |
| PvP assistance | OK | No PvP features |
| Unfair advantage over vanilla | Low risk | Self-imposed ironman rules; default does not block server actions |
| Player data exfiltration | OK | No network |

### RuneLite technical rules

| Area | Status | Notes |
|------|--------|-------|
| Java 11 / forbidden language | OK | No reflection on game classes, JNI, Unsafe, process execution |
| File I/O | OK | Confined to `.runelite/group-slot-locked/` |
| Item IDs | OK | Hardcoded `gameval` `ItemID.WILDERNESS_CAPE_*` in `SlotType`; `customTokens` config unused |
| New server-action menu entries | OK | No entries added |
| Construction / Blackjack menus | OK | Not touched |
| Click-zone resizing | OK | Overlays are draw-only; no inventory/equipment/prayer/spellbook click-zone changes |
| Outgoing chat modification | OK | Does not alter messages the user sends |
| Boss / combat / PvP rejected features | OK | N/A |

---

## Gray Area — Review Risks

These are unlikely Jagex violations but may draw scrutiny from RuneLite Plugin Hub reviewers.

### 1. `blockIllegalEquips` (default: **off**) — highest hub risk

**File:** `SlotMenuHandler.onMenuOptionClicked()`

When enabled, calls `event.consume()` on Wear/Wield clicks for token items and gear that fails local validation. This **blocks the equip action client-side** before it reaches the server.

- Default off is compliance-friendly
- Similar to click-blocker plugins — reviewers often scrutinize these
- **Recommendation:** Keep off by default; describe clearly as optional self-enforcement in hub listing

### 2. `deprioritizeIllegalEquips` (default: **on**)

**File:** `SlotMenuHandler` (`onMenuEntryAdded`, `onMenuOpened`)

Moves Wear/Wield/Equip below other options when gear fails validation. Conditional menu manipulation based on **local item rules**, not NPC type, friend status, or PvP targeting.

RuneLite's rule targets conditional removal based on NPC/player context. This is closer to self-restriction on your own gear. Many restriction-helper plugins do similar things, but hub acceptance is not guaranteed.

### 3. Token left-click reordering (default: Examine)

**File:** `SlotMenuHandler.applyTokenMenuChanges()`, `prepareExamineForLeftClick()`

Promotes Examine/Drop/Use for Wilderness Cape tokens in inventory and uses `setForceLeftClick(true)` so left-click matches. Common RuneLite pattern (e.g. default left-click plugins). Low risk, but item-specific menu manipulation.

Bank withdraw/deposit left-click is **not** reordered — only target text is relabeled.

### 4. Chat examine suppression

**File:** `SlotMenuHandler.onScriptCallbackEvent("chatFilterCheck")`, `onChatMessage()`

After examining a token, filters the vanilla `ITEM_EXAMINE` message (~5 ticks) and optionally shows a plugin-authored message via `ChatMessageManager`.

- Does not modify outgoing chat
- Does filter incoming chat — unusual but not on the forbidden list
- Low–medium hub risk; fine for personal use

### 5. Penalty overlay (`penaltyOverlay`, default: **on**)

**File:** `ViolationOverlay`

Blacks out the game world while leaving inventory, equipment, and bank usable. Voluntary self-penalty — does not hide Jagex UI components or resize click zones. Unusual but aligned with challenge-mode intent.

### 6. Bank search script callback

**File:** `TokenBankSearchHandler.onScriptCallbackEvent("bankSearchFilter")`

Extends bank search so queries like `ring` match the Ring slot token. Same general approach as other RuneLite bank-search extensions. Low risk.

---

## Not a Problem

| Concern | Verdict |
|---------|---------|
| PvP deprioritization rule | Applies to **attack/cast on players**, not Wear on your own gear |
| Wilderness capes as tokens | Cosmetic stand-ins; no combat advantage |
| `customTokens` config | **Unused** today — if implemented as the entire ID source, could hit "player-provided IDs" content rule |
| Icon replacement / tooltips | Visual-only overlays; no click-zone changes |
| Red illegal-gear highlight | Visual feedback only |
| Sidebar panel | Standard navigation panel |

---

## Feature Reference

### Menu modifications (`SlotMenuHandler`)

| Modification | When | Mechanism |
|-------------|------|-----------|
| Relabel target | Token menu contexts (inv, bank, equipment) | `entry.setTarget(colored slot label)` |
| Relabel Examine option | Examine entries | `entry.setOption("Examine")` + slot target |
| Deprioritize Wear/Wield/Use/Drop | Inventory left-click reorder context | `entry.setDeprioritized(true)` per `tokenLeftClick` |
| Promote preferred left-click | Inventory, menu open or hover | `promoteEntry()` moves preferred option to top |
| Force left-click Examine | `tokenLeftClick = EXAMINE` | `entry.setType(CC_OP)`, `setForceLeftClick(true)` |
| Deprioritize illegal equips | `deprioritizeIllegalEquips` on | `setDeprioritized(true)` on Wear/Wield for failing gear |
| Consume equip click | `blockIllegalEquips` on | `event.consume()` on Wear/Wield |

Does **not** add new menu entries.

### Overlays

| Overlay | Renders |
|---------|---------|
| `ItemRestrictionOverlay` | Token icon replacement; red fill on illegal gear |
| `TokenInventoryDragOverlay` / `TokenBankDragOverlay` | Custom token icon on drag ghosts |
| `TokenPressHoldOverlay` | Custom icon on click-hold |
| `TokenItemDragOverlay` | Custom icon on bank tag drag layer |
| `TokenTooltipOverlay` | Slot-name tooltip; suppresses Item Stats box |
| `ViolationOverlay` | Black screen with cutouts for inv/equipment/bank |

### Config defaults

| Key | Default | Notes |
|-----|---------|-------|
| `enablePlugin` | `true` | Master toggle |
| `penaltyOverlay` | `true` | Black-screen penalty |
| `highlightRestricted` | `true` | Red overlay on illegal gear |
| `deprioritizeIllegalEquips` | `true` | Deprioritize Wear/Wield on illegal gear |
| `blockIllegalEquips` | **`false`** | Consume Wear/Wield clicks |
| `tokenLeftClick` | `EXAMINE` | Default left-click for tokens |
| `tokenExamineHint` | `true` | Custom chat on token examine |
| `replaceTokenIcons` | `true` | Replace cape sprites |
| `chatWarnings` | `true` | Chat on illegal loadout / token cap |
| `customTokens` | `""` | **Unused** (future) |

---

## Bottom Line

| Audience | Assessment |
|----------|------------|
| **Jagex / playing the game** | OK — self-imposed ironman helper, no cheating or automation |
| **RuneLite personal dev use** | OK |
| **RuneLite Plugin Hub** | Probably OK with caveats — strongest risks are `blockIllegalEquips` and default-on `deprioritizeIllegalEquips` |

---

## Hub Submission Recommendations

1. Keep `blockIllegalEquips` **off by default** (already done).
2. Describe the plugin as **voluntary team challenge rules**, not Jagex-recognized enforcement.
3. Be prepared to explain that deprioritization is **self-restriction on your own gear**, not PvP/NPC menu targeting.
4. Do not ship `customTokens` until the design avoids relying entirely on user-provided item IDs (see [Custom tokens (`customTokens`) — design guidance](#custom-tokens-customtokens--design-guidance) below).
5. Consider hub description language such as:
   - "Self-imposed Group Ironman slot claim system"
   - "Visual warnings and optional click deprioritization — equip blocking is opt-in and off by default"
   - "Does not communicate with external servers"

---

## Feature Violation Matrix

| Feature | Jagex | RuneLite hub risk |
|---------|-------|-------------------|
| Slot token tracking & validation | OK | OK |
| Sidebar panel | OK | OK |
| Red illegal-gear highlight | OK | OK |
| Penalty black-screen overlay | OK | Low–medium |
| Token icon replacement | OK | OK |
| Token menu relabel / reorder / force left-click | OK | Low |
| Deprioritize illegal equips (default on) | OK | Medium |
| Block illegal equips (default off) | Review | Medium–high when enabled |
| Token examine chat + suppress vanilla | OK | Low–medium |
| Bank search slot-name matching | OK | Low |
| Chat violation warnings | OK | OK |
| Custom icons/names (local files) | OK | OK |
| `customTokens` (unused) | OK if stays unused | — |

---

## Custom tokens (`customTokens`) — design guidance

### What the RuneLite rule actually says

From [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features):

> **ID based plugins:** Plugins that use player provided IDs for the entirety of their functionality can cause moderation issues and outcomes that break Jagex's plugin rules. Due to this fact we will not be accepting any new ID based plugins. Plugins that use a specific set of IDs but do not allow user input will still be accepted. (e.g. plugins like Vardorvis Projectiles which only allows you to change a specific projectile to a set list of projectiles.)

The rejection is not about mentioning item IDs in code. It is about plugins whose **core behavior is driven by open-ended user-supplied IDs**, which:

- Cannot be reviewed reliably ("is this ID used for a harmless ironman token or to highlight a PvP target's gear?")
- Can be repurposed into generic highlighters, menu modifiers, or swapper tools that violate other rules
- Create moderation burden when users paste arbitrary IDs into a config field

**Group Slot Locked today is hub-friendly** because all 11 token IDs are hardcoded in `SlotType` via `gameval` constants (`ItemID.WILDERNESS_CAPE_*`). The plugin works out of the box with zero user ID input.

### What `customTokens` was planned for

`GroupSlotLockedConfig.customTokens()` is a placeholder:

```java
@ConfigItem(
    keyName = "customTokens",
    name = "Custom tokens",
    description = "Future: override token item IDs per slot")
default String customTokens() {
  return "";
}
```

A naive implementation might look like:

```
head=12345,ring=67890,body=11111
```

where users paste any item ID per slot. That pattern is exactly what hub reviewers reject.

### Why "entire functionality" matters

The rule targets plugins where **nothing useful happens until the user configures IDs**. Examples that get rejected:

- "Enter object IDs to highlight"
- "Enter NPC IDs to mark"
- "Enter item IDs to swap menu behavior on"

If `customTokens` became the **only** way to define which items are tokens, the plugin would become an open-ended item-ID menu/overlay engine. Even if your intent is innocent (teams using different cape colors), reviewers cannot verify how each user will configure it.

### Safe vs risky designs

| Design | Hub outlook | Notes |
|--------|-------------|-------|
| **Hardcoded 11 cape IDs only** (current) | OK | Fixed, documented set; no user ID input |
| **Curated dropdown per slot** | OK | User picks from a developer-defined list (e.g. all Team cape / Wilderness cape variants) — same pattern as Vardorvis Projectiles |
| **Optional override on top of defaults** | Probably OK | Plugin fully works with built-in IDs; user can optionally remap *specific slots* to IDs from an allowlist |
| **Free-form `head=12345` text config** | Rejected | Open-ended user ID input for core token detection |
| **Plugin useless without custom IDs** | Rejected | Entire functionality depends on user-supplied IDs |

### Recommended approach if you implement it

1. **Keep `SlotType` defaults as the source of truth.** The 11 `WILDERNESS_CAPE_*` IDs remain the built-in token set. The plugin must work with an empty `customTokens` value.

2. **Use an allowlist, not free text.** If teams want different cape colors, define acceptable alternatives in code:

   ```java
   // Example: per-slot allowed token IDs (all verified team/wilderness capes)
   private static final Map<SlotType, Set<Integer>> ALLOWED_TOKEN_IDS = ...
   ```

   Config UI: dropdown or multi-select from that set, not a raw integer text box.

3. **Scope overrides narrowly.** Overrides change *which item counts as the token for a slot*, not *which arbitrary items get menu manipulation*. All other plugin logic (equip validation, slot claims, overlays) stays tied to the slot model, not arbitrary items.

4. **Do not use custom IDs for non-token features.** Illegal-gear highlighting and equip deprioritization should remain based on equipment slot rules and validation — not "user pasted this weapon ID, block equipping it."

5. **Document the fixed set in the hub listing.** e.g. "Uses a predefined set of 11 wilderness cape tokens; optional per-slot variants from a curated list."

### What you can do today without `customTokens`

These are already compliant and cover most team customization needs:

| Feature | Config | Compliant? |
|---------|--------|------------|
| Custom slot **names** | `customSlotNames` | Yes — text labels, not item IDs |
| Custom slot **icons** | `.runelite/group-slot-locked/icons/` | Yes — local PNG files |
| Bank search by slot name | `TokenBankSearchHandler` | Yes — matches display names |

Teams can rename "Ring slot" to "Utility slot" and use custom icons without changing which cape item ID is the token.

### Personal use vs hub

- **Personal / sideload:** Free-form `customTokens` is fine for your own group if you want it.
- **Plugin Hub:** Ship only a curated, reviewable design. When in doubt, leave `customTokens` unimplemented and remove the config key from the hub build so reviewers do not see a dormant "paste item IDs here" field.

### Checklist before shipping `customTokens`

- [ ] Plugin works fully with default hardcoded IDs and empty config
- [ ] User cannot enter arbitrary integer IDs (allowlist or enum picker only)
- [ ] Allowlist IDs are defined in code and documented
- [ ] Overrides affect token detection only, not open-ended menu/overlay targeting
- [ ] Hub description states tokens come from a fixed/curated set
