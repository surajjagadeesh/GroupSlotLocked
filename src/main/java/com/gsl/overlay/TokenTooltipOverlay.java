package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.SlotType;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.BankItemUtils;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.annotation.Nullable;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

/**
 * Replaces RuneLite Item Stats floating tooltips on token hovers with a slot-name tooltip
 * (e.g. {@code Examine} + orange {@code Ring slot}). This is the box near the cursor when
 * hovering an item in inventory/bank.
 */
public class TokenTooltipOverlay extends Overlay {
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final TooltipManager tooltipManager;
  private final ItemManager itemManager;
  private final SlotDisplayService displayService;

  @Inject
  TokenTooltipOverlay(
      Client client,
      GroupSlotLockedConfig config,
      TooltipManager tooltipManager,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    this.client = client;
    this.config = config;
    this.tooltipManager = tooltipManager;
    this.itemManager = itemManager;
    this.displayService = displayService;
    setPosition(OverlayPosition.TOOLTIP);
    // Run after Item Stats adds its tooltip, but before TooltipOverlay draws.
    setPriority(PRIORITY_HIGHEST + 0.01f);
    setLayer(OverlayLayer.ABOVE_WIDGETS);
    drawAfterInterface(InterfaceID.TOPLEVEL_DISPLAY);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (client.isMenuOpen()) {
      return null;
    }
    HoveredToken hovered = resolveHoveredToken();
    if (hovered == null) {
      return null;
    }
    tooltipManager.clear();
    tooltipManager.add(new Tooltip(formatTokenTooltip(hovered)));
    return null;
  }

  private String formatTokenTooltip(HoveredToken hovered) {
    String slotLabel =
        ColorUtil.wrapWithColorTag(
            displayService.getHoverTargetText(hovered.slot, hovered.quantity),
            JagexColors.MENU_TARGET);
    String option = hovered.option;
    if (option == null || option.isEmpty()) {
      return slotLabel;
    }
    return Text.removeTags(option) + " " + slotLabel;
  }

  @Nullable
  private HoveredToken resolveHoveredToken() {
    MenuEntry[] menu = client.getMenu().getMenuEntries();
    if (menu.length == 0) {
      return null;
    }
    MenuEntry top = menu[menu.length - 1];
    int itemId = resolveHoveredItemId(top);
    SlotType slot = BankItemUtils.resolveSlotType(itemManager, itemId);
    if (slot == null) {
      return null;
    }
    int quantity = 1;
    Widget widget = top.getWidget();
    if (widget != null) {
      quantity =
          BankItemUtils.resolvePlaceholderDisplayQuantity(
              itemManager, widget.getItemId(), widget.getItemQuantity());
    }
    return new HoveredToken(slot, quantity, top.getOption());
  }

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
        || group == InterfaceID.INVENTORY
        || group == InterfaceID.EQUIPMENT_SIDE
        || widget.getId() == InterfaceID.Bankmain.ITEMS
        || group == InterfaceID.BANKMAIN
        || group == InterfaceID.BANKSIDE
        || widget.getId() == InterfaceID.SharedBank.ITEMS
        || group == InterfaceID.SHARED_BANK
        || group == InterfaceID.SHARED_BANK_SIDE) {
      return widget.getItemId();
    }

    int itemId = entry.getItemId();
    if (itemId > 0) {
      return itemId;
    }
    return widget.getItemId();
  }

  private static final class HoveredToken {
    private final SlotType slot;
    private final int quantity;
    private final String option;

    private HoveredToken(SlotType slot, int quantity, String option) {
      this.slot = slot;
      this.quantity = quantity;
      this.option = option;
    }
  }
}
