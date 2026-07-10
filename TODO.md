# Group Slot Locked — Future Improvements

Tracked ideas and follow-up work. Not yet implemented unless marked done.

---

## Ground items (dropped tokens)

- [ ] **Render slot icon on dropped tokens** — When a slot token is dropped on the ground, replace the wilderness cape sprite with the matching equipment-slot icon (same visual treatment as inventory/bank overlays).
- [ ] **Rename dropped token on the ground** — Change the ground item name/hover text from the cape name to the slot label (e.g. `Head slot` instead of `Team-6 cape`).

**Notes:** Likely needs a ground/tile-item overlay or menu/hover hook similar to `TokenTooltipOverlay` / `ItemRestrictionOverlay`. Check RuneLite APIs for tile items, ground item menus, and item pile rendering.

---

## Trading & exchange UI

- [ ] **Test trading with tokens** — Verify slot icons, names, tooltips, and menu text look correct when tokens are offered in a trade (both sides). Confirm no cape sprite/name leaks through in the trade window or ground pickup flow.

**Test checklist:**
- [ ] Trade window shows slot icon (not cape)
- [ ] Trade window shows slot name on hover / examine
- [ ] Accept/decline flow unchanged (no menu regressions)
- [ ] Picked-up traded token still shows slot icon in inventory

---

## Backlog (add items here)

<!-- Future todos go below -->
