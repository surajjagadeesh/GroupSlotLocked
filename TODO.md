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

- [ ] **cleanup: stale static drag state not reset across plugin disable/enable** — `TokenDragIconRenderer` keeps drag-tracking state (`dragGrabOffset`, hold anchors, `dragHandoffReady`) in `private static` fields with no reset hook called from `GroupSlotLockedPlugin.shutDown()`/`startUp()`. Mostly self-correcting, but could cause a stale visual glitch on the first drag right after re-enabling the plugin.
- [ ] **known limitation, accepted for now: stationary click-and-hold in group storage's inventory panel shows the raw cape** — clicking and holding (no mouse movement) a slot token in the inventory panel while group storage is open still shows the raw cape until the mouse actually moves. Confirmed the widget is `SharedBankSide.ITEMS`; its item layer doesn't redraw on a stationary press so the usual `drawAfterLayer` cover never fires, and a dedicated `ALWAYS_ON_TOP` pass (`TokenDragIconRenderer.renderSharedBankSideStationaryHoldCover`) didn't fix it either. Low priority — narrow edge case, rarely drag from this panel.

---

## Backlog (add items here)

<!-- Future todos go below -->
- [ ] possibly add the ability to customize which capes are used for tokens so players don't have to get exactly the ones we call for. this should be under an advanced tab. if not configurable, then just add an info box in the plugin settings that says which capes correspond to which slot token items. Also which guy sells them.