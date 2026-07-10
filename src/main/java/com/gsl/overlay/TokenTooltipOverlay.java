package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.SlotType;
import com.gsl.util.BankItemUtils;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.game.ItemManager;

/**
 * Strips RuneLite Item Stats tooltips when hovering token items and their bank placeholders.
 * Both reuse wilderness cape IDs, so Item Stats would otherwise show cape equipment bonuses.
 */
public class TokenTooltipOverlay extends Overlay {
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final TooltipManager tooltipManager;
  private final ItemManager itemManager;

  @Inject
  TokenTooltipOverlay(
      Client client,
      GroupSlotLockedConfig config,
      TooltipManager tooltipManager,
      ItemManager itemManager) {
    this.client = client;
    this.config = config;
    this.tooltipManager = tooltipManager;
    this.itemManager = itemManager;
    setPosition(OverlayPosition.TOOLTIP);
    // TOOLTIP overlays with higher priority render before TooltipOverlay (PRIORITY_HIGHEST).
    setPriority(PRIORITY_HIGHEST + 0.01f);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
    drawAfterInterface(InterfaceID.TOPLEVEL_DISPLAY);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (!config.enablePlugin() || client.isMenuOpen()) {
      return null;
    }
    if (resolveHoveredTokenSlot() != null) {
      tooltipManager.clear();
    }
    return null;
  }

  private SlotType resolveHoveredTokenSlot() {
    MenuEntry[] menu = client.getMenuEntries();
    if (menu.length == 0) {
      return null;
    }
    int itemId = resolveHoveredItemId(menu[menu.length - 1]);
    return BankItemUtils.resolveSlotType(itemManager, itemId);
  }

  /**
   * Mirrors ItemStatOverlay item-id resolution so we suppress stats in exactly the same contexts.
   */
  private static int resolveHoveredItemId(MenuEntry entry) {
    Widget widget = entry.getWidget();
    if (widget == null) {
      return entry.getItemId();
    }

    int group = WidgetUtil.componentToInterface(widget.getId());
    if (group == InterfaceID.WORNITEMS
        || (group == InterfaceID.BANKMAIN
            && widget.getParentId() == InterfaceID.Bankside.WORNOPS)) {
      Widget itemWidget = widget.getChild(1);
      return itemWidget != null ? itemWidget.getItemId() : -1;
    }

    if (widget.getId() == InterfaceID.Inventory.ITEMS
        || group == InterfaceID.EQUIPMENT_SIDE
        || widget.getId() == InterfaceID.Bankmain.ITEMS
        || group == InterfaceID.BANKSIDE
        || widget.getId() == InterfaceID.SharedBank.ITEMS
        || group == InterfaceID.SHARED_BANK_SIDE) {
      return widget.getItemId();
    }

    int itemId = entry.getItemId();
    if (itemId > 0) {
      return itemId;
    }
    return widget.getItemId();
  }
}
