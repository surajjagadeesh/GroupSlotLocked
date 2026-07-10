package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.TokenDragIconRenderer;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TokenItemDragOverlay extends Overlay {
  private final Client client;
  private final ItemManager itemManager;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;

  @Inject
  TokenItemDragOverlay(
      Client client,
      ItemManager itemManager,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService) {
    this.client = client;
    this.itemManager = itemManager;
    this.config = config;
    this.displayService = displayService;
    setLayer(OverlayLayer.ALWAYS_ON_TOP);
    setPosition(OverlayPosition.DYNAMIC);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (!config.enablePlugin() || !config.replaceTokenIcons()) {
      return null;
    }
    TokenDragIconRenderer.renderDraggedWidgetIcon(graphics, client, itemManager, displayService);
    TokenDragIconRenderer.renderPressedInventoryItemIcon(graphics, client, itemManager, displayService);
    TokenDragIconRenderer.renderPressedBankItemIcon(graphics, client, itemManager, displayService);
    return null;
  }
}
