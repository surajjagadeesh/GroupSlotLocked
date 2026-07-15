package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.TokenDragIconRenderer;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Draws bank drag ghosts after the full bank interface is rendered, matching inventory drag
 * behavior. Static icons use {@link ItemRestrictionOverlay} on item layers instead.
 */
public class TokenBankDragOverlay extends WidgetItemOverlay {
  private final Client client;
  private final ItemManager itemManager;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;

  @Inject
  TokenBankDragOverlay(
      Client client,
      ItemManager itemManager,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService) {
    this.client = client;
    this.itemManager = itemManager;
    this.config = config;
    this.displayService = displayService;
    drawAfterInterface(InterfaceID.BANKMAIN);
    drawAfterInterface(InterfaceID.SHARED_BANK);
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
    if (!config.replaceTokenIcons()) {
      return;
    }
    TokenDragIconRenderer.renderBankInterfaceItemIcon(
        graphics, client, itemManager, displayService, itemId, widgetItem);
  }
}
