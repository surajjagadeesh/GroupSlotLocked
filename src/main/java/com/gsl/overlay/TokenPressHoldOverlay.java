package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.TokenDragIconRenderer;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Covers the press-state item sprite during click-and-hold without mouse movement.
 *
 * <p>Must use {@code drawAfterInterface}/{@code drawAfterLayer} so the cover is baked into the UI
 * texture before GPU compositing. {@code ALWAYS_ON_TOP} runs too early when the GPU plugin is on.
 *
 * <p>Exception: the inventory panel while group storage is open (SharedBankSide.ITEMS) isn't
 * hooked here — its item layer doesn't redraw on a stationary press, so the hook never fires.
 * That one container is covered by {@code TokenItemDragOverlay}'s {@code ALWAYS_ON_TOP} pass
 * instead, via {@code TokenDragIconRenderer.renderSharedBankSideStationaryHoldCover}.
 */
public class TokenPressHoldOverlay extends Overlay {
  private final Client client;
  private final ItemManager itemManager;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;

  @Inject
  TokenPressHoldOverlay(
      Client client,
      ItemManager itemManager,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService) {
    this.client = client;
    this.itemManager = itemManager;
    this.config = config;
    this.displayService = displayService;
    setPosition(OverlayPosition.DYNAMIC);
    setLayer(OverlayLayer.MANUAL);
    setPriority(PRIORITY_HIGH);
    drawAfterInterface(InterfaceID.INVENTORY);
    drawAfterInterface(InterfaceID.BANKSIDE);
    drawAfterInterface(InterfaceID.BANKMAIN);
    drawAfterInterface(InterfaceID.SHARED_BANK);
    drawAfterInterface(InterfaceID.TRADESIDE);
    drawAfterLayer(InterfaceID.Inventory.ITEMS);
    drawAfterLayer(InterfaceID.Bankside.ITEMS);
    drawAfterLayer(InterfaceID.Bankmain.ITEMS);
    drawAfterLayer(InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS);
    drawAfterLayer(InterfaceID.SharedBank.ITEMS);
    drawAfterLayer(InterfaceID.Tradeside.SIDE_LAYER);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (!config.replaceTokenIcons()) {
      return null;
    }
    TokenDragIconRenderer.renderStationaryHoldCover(
        graphics, client, itemManager, displayService);
    return null;
  }
}
