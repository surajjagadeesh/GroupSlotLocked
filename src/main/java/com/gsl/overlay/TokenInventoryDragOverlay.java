package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.BankItemUtils;
import com.gsl.util.TokenDragIconRenderer;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws inventory drag ghosts after the full interface is rendered, covering the vanilla drag
 * sprite. Static icons use {@link ItemRestrictionOverlay} on item layers instead.
 */
public class TokenInventoryDragOverlay extends WidgetItemOverlay {
  private final Client client;
  private final ItemManager itemManager;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;

  @Inject
  TokenInventoryDragOverlay(
      Client client,
      ItemManager itemManager,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService) {
    this.client = client;
    this.itemManager = itemManager;
    this.config = config;
    this.displayService = displayService;
    drawAfterInterface(InterfaceID.INVENTORY);
    drawAfterInterface(InterfaceID.BANKSIDE);
    drawAfterInterface(InterfaceID.SHARED_BANK_SIDE);
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
    if (!config.enablePlugin() || !config.replaceTokenIcons()) {
      return;
    }
    if (BankItemUtils.resolveSlotType(itemManager, itemId) == null) {
      return;
    }
    TokenDragIconRenderer.renderInventoryDragWidgetItemIcon(
        graphics, client, itemManager, displayService, itemId, widgetItem);
  }
}
