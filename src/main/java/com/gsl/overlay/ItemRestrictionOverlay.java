package com.gsl.overlay;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.SlotType;
import com.gsl.model.Violation;
import com.gsl.service.SlotDisplayService;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import com.gsl.util.BankItemUtils;
import com.gsl.util.TokenDragIconRenderer;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class ItemRestrictionOverlay extends WidgetItemOverlay {
  private final Client client;
  private final ItemManager itemManager;
  private final GroupSlotLockedConfig config;
  private final SlotStateService slotStateService;
  private final SlotValidator slotValidator;
  private final SlotDisplayService displayService;

  @Inject
  ItemRestrictionOverlay(
      Client client,
      ItemManager itemManager,
      GroupSlotLockedConfig config,
      SlotStateService slotStateService,
      SlotValidator slotValidator,
      SlotDisplayService displayService) {
    this.client = client;
    this.itemManager = itemManager;
    this.config = config;
    this.slotStateService = slotStateService;
    this.slotValidator = slotValidator;
    this.displayService = displayService;
    // Register only on item layers, never on whole interfaces. showOnInventory/showOnBank
    // use drawAfterInterface, which runs before item sprites and leaves the cape visible.
    drawAfterLayer(InterfaceID.Inventory.ITEMS);
    drawAfterLayer(InterfaceID.Bankside.ITEMS);
    drawAfterLayer(InterfaceID.Bankmain.ITEMS);
    drawAfterLayer(InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS);
    drawAfterLayer(InterfaceID.Bankmain.BANKTAGS_DISPLAY_DRAGLAYER);
    drawAfterLayer(InterfaceID.EquipmentSide.ITEMS);
    drawAfterLayer(InterfaceID.SharedBank.ITEMS);
    drawAfterLayer(InterfaceID.SharedBankSide.ITEMS);
    drawAfterLayer(InterfaceID.Tradeside.SIDE_LAYER);
    drawAfterLayer(InterfaceID.Trademain.YOUR_OFFER);
    drawAfterLayer(InterfaceID.Trademain.OTHER_OFFER);
  }

  @Override
  public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
    Rectangle bounds = widgetItem.getCanvasBounds();
    if (config.replaceTokenIcons() && BankItemUtils.resolveSlotType(itemManager, itemId) != null) {
      TokenDragIconRenderer.renderWidgetItemIcon(
          graphics, client, itemManager, displayService, itemId, widgetItem);
    }
    if (!config.highlightRestricted()) {
      return;
    }
    if (BankItemUtils.isPlaceholderItem(itemManager, itemId, widgetItem.getQuantity())) {
      return;
    }
    int canonicalId = itemManager.canonicalize(itemId);
    Violation violation = slotValidator.getViolationForItem(slotStateService.getState(), canonicalId);
    if (violation == Violation.NONE) {
      return;
    }
    BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), Color.RED);
    if (outline == null) {
      return;
    }
    graphics.drawImage(outline, (int) bounds.getX(), (int) bounds.getY(), null);
  }
}
