# Group Slot Locked — Future Improvements

Tracked ideas and follow-up work. Not yet implemented unless marked done.

---

## Trading & exchange UI

- [ ] **Test trading with tokens** — Verify slot icons, names, tooltips, and menu text look correct when tokens are offered in a trade (both sides). Confirm no cape sprite/name leaks through in the trade window or ground pickup flow.

**Test checklist:**
- [ ] Trade window shows slot icon (not cape)
- [ ] Trade window shows slot name on hover / examine
- [ ] Accept/decline flow unchanged (no menu regressions)
- [ ] Picked-up traded token still shows slot icon in inventory

---

## Known bugs (from repo scan)

- [ ] **cleanup: dead code in TokenDragIconRenderer** — `resolveActiveDragBounds` (private) plus public `renderInventoryDragWidgetItemIcon`, `renderBankDragWidgetItemIcon`, `isItemBeingDragged`, `renderPressedInventoryItemIcon`, `renderPressedBankItemIcon` have zero callers anywhere in the codebase.
- [ ] **cleanup: stale static drag state not reset across plugin disable/enable** — `TokenDragIconRenderer` keeps drag-tracking state (`dragGrabOffset`, hold anchors, `dragHandoffReady`) in `private static` fields with no reset hook called from `GroupSlotLockedPlugin.shutDown()`/`startUp()`. Mostly self-correcting, but could cause a stale visual glitch on the first drag right after re-enabling the plugin.
- [ ] **bug (unconfirmed): red highlight doesn't cover the dedicated Worn Equipment tab** — `ItemRestrictionOverlay` only hooks `EquipmentSide.ITEMS` (the compact side panel), not the full `Equipment.SLOT0..13` widgets used by the dedicated Equipment tab, so illegally-worn gear shows no red highlight there (only the check/cross badges from `EquipmentTokenClaimOverlay`). May be an intentional pre-existing scope decision rather than a bug — needs a decision.

---

## Backlog (add items here)

<!-- Future todos go below -->
- [ ] possibly add the ability to customize which capes are used for tokens so players don't have to get exactly the ones we call for. this should be under an advanced tab. if not configurable, then just add an info box in the plugin settings that says which capes correspond to which slot token items. Also which guy sells them.
- [ ] bug: when in group storage the scaperune teleport item still shows up on top of the slot icon overlay when dragging the item
- [ ] bug: when moving an item from bank to inventory, the bank check has not yet run and it might still have a stale number like 5, when there now is a moved slot token in the inventory. So the text message of extra items will be displayed. We should probably delay the check for exceeding the limit until after the bank interface is closed.