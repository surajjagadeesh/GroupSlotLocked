package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.SlotType;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a green check (token held) or red X (no token) badge on each slot of the full Worn
 * Equipment tab, so claim status is visible at a glance.
 */
public class EquipmentTokenClaimOverlay extends Overlay {
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final SlotStateService slotStateService;
  private final SlotValidator slotValidator;
  private final SpriteManager spriteManager;

  @Inject
  EquipmentTokenClaimOverlay(
      Client client,
      GroupSlotLockedConfig config,
      SlotStateService slotStateService,
      SlotValidator slotValidator,
      SpriteManager spriteManager) {
    this.client = client;
    this.config = config;
    this.slotStateService = slotStateService;
    this.slotValidator = slotValidator;
    this.spriteManager = spriteManager;
    setLayer(OverlayLayer.ALWAYS_ON_TOP);
    setPosition(OverlayPosition.DYNAMIC);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (!config.showEquipmentIndicators()) {
      return null;
    }
    BufferedImage tick = spriteManager.getSprite(SpriteID.TICK, 0);
    BufferedImage cross = spriteManager.getSprite(SpriteID.CROSS, 0);
    if (tick == null || cross == null) {
      return null;
    }
    for (SlotType slot : SlotType.values()) {
      Widget slotWidget = client.getWidget(componentIdFor(slot.getEquipmentSlot()));
      if (slotWidget == null || slotWidget.isHidden()) {
        continue;
      }
      Point location = slotWidget.getCanvasLocation();
      if (location == null) {
        continue;
      }
      boolean claimed = slotValidator.hasActiveClaim(slotStateService.getState(), slot);
      BufferedImage icon = claimed ? tick : cross;
      int x = location.getX() + slotWidget.getWidth() - icon.getWidth();
      int y = location.getY() + slotWidget.getHeight() - icon.getHeight();
      graphics.drawImage(icon, x, y, null);
    }
    return null;
  }

  private static int componentIdFor(EquipmentInventorySlot slot) {
    switch (slot) {
      case HEAD:
        return InterfaceID.Equipment.SLOT0;
      case CAPE:
        return InterfaceID.Equipment.SLOT1;
      case AMULET:
        return InterfaceID.Equipment.SLOT2;
      case WEAPON:
        return InterfaceID.Equipment.SLOT3;
      case BODY:
        return InterfaceID.Equipment.SLOT4;
      case SHIELD:
        return InterfaceID.Equipment.SLOT5;
      case LEGS:
        return InterfaceID.Equipment.SLOT7;
      case GLOVES:
        return InterfaceID.Equipment.SLOT9;
      case BOOTS:
        return InterfaceID.Equipment.SLOT10;
      case RING:
        return InterfaceID.Equipment.SLOT12;
      case AMMO:
        return InterfaceID.Equipment.SLOT13;
      default:
        return -1;
    }
  }
}
