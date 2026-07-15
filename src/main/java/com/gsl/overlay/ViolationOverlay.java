package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class ViolationOverlay extends Overlay {
  private static final int SHARED_BANK_ITEMS =
      WidgetUtil.packComponentId(InterfaceID.SHARED_BANK, 2);
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final SlotStateService slotStateService;
  private final SlotValidator slotValidator;

  @Inject
  ViolationOverlay(
      Client client,
      GroupSlotLockedConfig config,
      SlotStateService slotStateService,
      SlotValidator slotValidator) {
    this.client = client;
    this.config = config;
    this.slotStateService = slotStateService;
    this.slotValidator = slotValidator;
    setLayer(OverlayLayer.ABOVE_SCENE);
    setPosition(OverlayPosition.DYNAMIC);
  }

  @Override
  public Dimension render(Graphics2D graphics) {
    if (!config.enablePlugin() || !config.penaltyOverlay()) {
      return null;
    }
    if (!slotValidator.isLoadoutIllegal(slotStateService.getState())) {
      return null;
    }
    final Rectangle canvas = new Rectangle(client.getCanvas().getSize());
    graphics.setColor(Color.BLACK);
    graphics.fill(canvas);
    cutOutWidget(graphics, InterfaceID.Inventory.ITEMS);
    cutOutWidget(graphics, InterfaceID.EquipmentSide.ITEMS);
    cutOutWidget(graphics, InterfaceID.Bankmain.ITEMS);
    cutOutWidget(graphics, SHARED_BANK_ITEMS);
    return canvas.getSize();
  }

  private void cutOutWidget(Graphics2D graphics, int componentId) {
    Widget widget = client.getWidget(componentId);
    if (widget == null || widget.isHidden()) {
      return;
    }
    Point location = widget.getCanvasLocation();
    if (location == null) {
      return;
    }
    graphics.clearRect(location.getX(), location.getY(), widget.getWidth(), widget.getHeight());
  }
}
