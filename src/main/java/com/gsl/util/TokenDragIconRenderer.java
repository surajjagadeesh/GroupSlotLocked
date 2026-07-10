package com.gsl.util;

import com.gsl.model.SlotType;
import com.gsl.model.TokenIconStyle;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.BankItemUtils;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.annotation.Nullable;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;

public final class TokenDragIconRenderer {
  private static final int DRAG_ICON_SIZE = 36;
  private static Point dragGrabOffset;
  private static int dragGrabSourceId = -1;
  private static Point inventoryHoldAnchorMouse;
  private static int inventoryHoldAnchorSourceId = -1;
  private static Point bankHoldAnchorMouse;
  private static int bankHoldAnchorSourceId = -1;
  private static final int DRAG_MOVE_THRESHOLD_SQ = 4;
  private static final int[] BANK_ITEM_CONTAINERS = {
    InterfaceID.Bankmain.ITEMS,
    InterfaceID.SharedBank.ITEMS,
    InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS,
  };
  private static final int[] BANK_DRAG_LAYERS = {
    InterfaceID.Bankmain.BANKTAGS_DISPLAY_DRAGLAYER,
  };
  private static final int[] BANK_MAIN_ITEM_CONTAINERS = {
    InterfaceID.Bankmain.ITEMS,
    InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS,
    InterfaceID.SharedBank.ITEMS,
  };
  private static final int[] INVENTORY_ITEM_CONTAINERS = {
    InterfaceID.Inventory.ITEMS,
    InterfaceID.Bankside.ITEMS,
    InterfaceID.SharedBankSide.ITEMS,
  };

  private TokenDragIconRenderer() {}

  public static void renderStaticWidgetItemIcon(
      Graphics2D graphics,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    SlotType slot = slotFromItemId(itemManager, itemId);
    if (slot == null) {
      return;
    }
    drawTokenIcon(
        graphics,
        itemManager,
        displayService,
        slot,
        widgetItem.getCanvasBounds(false),
        itemId,
        widgetItem.getQuantity(),
        false);
  }

  public static void renderWidgetItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    SlotType slot = slotFromItemId(itemManager, itemId);
    if (slot == null) {
      return;
    }

    if (isDraggedWidgetItem(client, widgetItem)) {
      return;
    }

    renderStaticWidgetItemIcon(graphics, itemManager, displayService, itemId, widgetItem);
  }

  public static void renderInventoryInterfaceItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    if (isBankMainSourceWidget(widgetItem.getWidget())) {
      return;
    }
    if (tryRenderStationaryHoldWidgetCover(
        graphics, client, itemManager, displayService, widgetItem, false)) {
      return;
    }
    if (slotFromItemId(itemManager, itemId) == null) {
      return;
    }
    renderInventoryDragWidgetItemIcon(
        graphics, client, itemManager, displayService, itemId, widgetItem);
  }

  public static void renderBankInterfaceItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    if (!isBankMainSourceWidget(widgetItem.getWidget())) {
      return;
    }
    if (tryRenderStationaryHoldWidgetCover(
        graphics, client, itemManager, displayService, widgetItem, true)) {
      return;
    }
    if (slotFromItemId(itemManager, itemId) == null) {
      return;
    }
    renderBankDragWidgetItemIcon(
        graphics, client, itemManager, displayService, itemId, widgetItem);
  }

  public static void renderStationaryHoldCover(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    if (client.getMouseCurrentButton() == 0) {
      clearInventoryHoldAnchor();
      clearBankHoldAnchor();
      return;
    }

    Widget dragged = client.getDraggedWidget();
    if (dragged != null) {
      if (isInventoryDragContainer(dragged) && isStationaryHoldDrag(client, dragged, false)) {
        drawStationaryHoldForDrag(graphics, client, itemManager, displayService, dragged);
      } else if (isBankMainDragSource(dragged) && isStationaryHoldDrag(client, dragged, true)) {
        drawStationaryHoldForDrag(graphics, client, itemManager, displayService, dragged);
      }
      return;
    }

    Point mouse = client.getMouseCanvasPosition();
    if (mouse == null) {
      return;
    }

    for (int containerId : INVENTORY_ITEM_CONTAINERS) {
      if (drawStationaryHoldAtContainer(
          graphics,
          client,
          itemManager,
          displayService,
          containerId,
          mouse.getX(),
          mouse.getY())) {
        return;
      }
    }

    for (int containerId : BANK_ITEM_CONTAINERS) {
      if (drawStationaryHoldAtContainer(
          graphics,
          client,
          itemManager,
          displayService,
          containerId,
          mouse.getX(),
          mouse.getY())) {
        return;
      }
    }
  }

  private static boolean tryRenderStationaryHoldWidgetCover(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      WidgetItem widgetItem,
      boolean bankMain) {
    if (!isStationaryHoldWidgetItem(client, widgetItem, bankMain)) {
      return false;
    }
    HeldItemDetails held = resolveHeldItemDetails(client, client.getDraggedWidget());
    if (held == null) {
      int itemId = widgetItem.getId();
      if (slotFromItemId(itemManager, itemId) == null) {
        return false;
      }
      held = new HeldItemDetails(itemId, widgetItem.getQuantity());
    }
    if (slotFromItemId(itemManager, held.itemId) == null) {
      return false;
    }
    drawStationaryHoldAtWidget(
        graphics, client, itemManager, displayService, held, widgetItem.getWidget());
    return true;
  }

  private static boolean isStationaryHoldDrag(Client client, Widget dragged, boolean bankMain) {
    if (bankMain) {
      return !hasVisualBankDragStarted(client, dragged);
    }
    return !hasVisualInventoryDragStarted(client, dragged);
  }

  private static boolean isStationaryHoldWidgetItem(
      Client client, WidgetItem widgetItem, boolean bankMain) {
    if (client.getMouseCurrentButton() == 0) {
      return false;
    }
    Widget itemWidget = widgetItem.getWidget();
    if (bankMain) {
      if (!isBankMainSourceWidget(itemWidget)) {
        return false;
      }
    } else if (isBankMainSourceWidget(itemWidget)) {
      return false;
    }

    Widget dragged = client.getDraggedWidget();
    if (dragged == null || !isStationaryHoldDrag(client, dragged, bankMain)) {
      return false;
    }
    if (bankMain && !isBankMainDragSource(dragged)) {
      return false;
    }
    if (!bankMain && !isInventoryDragContainer(dragged)) {
      return false;
    }
    Widget heldSlot = resolveDraggedSlotWidget(client);
    return heldSlot != null && heldSlot == itemWidget;
  }

  private static void drawStationaryHoldForDrag(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      Widget dragged) {
    HeldItemDetails held = resolveHeldItemDetails(client, dragged);
    if (held == null || slotFromItemId(itemManager, held.itemId) == null) {
      return;
    }
    Widget heldSlot = resolveDraggedSlotWidget(client);
    drawStationaryHoldAtWidget(
        graphics, client, itemManager, displayService, held, heldSlot != null ? heldSlot : dragged);
  }

  private static void drawStationaryHoldAtWidget(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      HeldItemDetails held,
      @Nullable Widget slotWidget) {
    SlotType slot = slotFromItemId(itemManager, held.itemId);
    if (slot == null) {
      return;
    }
    Rectangle bounds = resolveActiveDragBounds(client, slotWidget);
    if (bounds == null && slotWidget != null) {
      bounds = getWidgetCanvasBounds(client, slotWidget);
    }
    if (bounds != null) {
      drawTokenIcon(
          graphics,
          itemManager,
          displayService,
          slot,
          bounds,
          held.itemId,
          held.quantity,
          true);
    }
  }

  private static boolean drawStationaryHoldAtContainer(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int containerId,
      int mouseX,
      int mouseY) {
    Widget container = client.getWidget(containerId);
    Widget hit = findItemChildAtCanvas(client, container, mouseX, mouseY);
    if (hit == null) {
      return false;
    }
    HeldItemDetails held = resolveHeldItemDetails(client, null, hit);
    if (held == null || slotFromItemId(itemManager, held.itemId) == null) {
      return false;
    }
    drawStationaryHoldAtWidget(graphics, client, itemManager, displayService, held, hit);
    return true;
  }

  @Nullable
  private static HeldItemDetails resolveHeldItemDetails(Client client, Widget dragged) {
    return resolveHeldItemDetails(client, dragged, resolveDraggedSlotWidget(client));
  }

  @Nullable
  private static HeldItemDetails resolveHeldItemDetails(
      Client client, @Nullable Widget dragged, @Nullable Widget heldSlot) {
    int itemId = heldSlot != null ? heldSlot.getItemId() : -1;
    int quantity = heldSlot != null ? heldSlot.getItemQuantity() : 0;
    if (itemId <= 0 && dragged != null) {
      ItemContainer container = containerForDragWidget(client, dragged.getId());
      if (container != null) {
        Item item = container.getItem(dragged.getIndex());
        if (item != null) {
          itemId = item.getId();
          quantity = item.getQuantity();
        }
      }
    }
    if (itemId <= 0 && heldSlot != null) {
      itemId = heldSlot.getItemId();
      quantity = heldSlot.getItemQuantity();
    }
    if (itemId <= 0) {
      return null;
    }
    return new HeldItemDetails(itemId, quantity);
  }

  @Nullable
  private static ItemContainer containerForDragWidget(Client client, int widgetId) {
    if (widgetId == InterfaceID.Inventory.ITEMS || widgetId == InterfaceID.Bankside.ITEMS) {
      return client.getItemContainer(InventoryID.INV);
    }
    if (widgetId == InterfaceID.Bankmain.ITEMS
        || widgetId == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS) {
      return client.getItemContainer(InventoryID.BANK);
    }
    if (widgetId == InterfaceID.SharedBank.ITEMS
        || widgetId == InterfaceID.SharedBankSide.ITEMS) {
      return client.getItemContainer(InventoryID.INV_GROUP_TEMP);
    }
    return null;
  }

  private static final class HeldItemDetails {
    private final int itemId;
    private final int quantity;

    private HeldItemDetails(int itemId, int quantity) {
      this.itemId = itemId;
      this.quantity = quantity;
    }
  }

  public static void renderInventoryDragWidgetItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    if (!isDraggedWidgetItem(client, widgetItem)
        || isBankMainSourceWidget(widgetItem.getWidget())) {
      return;
    }
    renderDragWidgetItemIcon(graphics, client, itemManager, displayService, itemId, widgetItem);
  }

  public static void renderBankDragWidgetItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    if (!isDraggedWidgetItem(client, widgetItem)
        || !isBankMainSourceWidget(widgetItem.getWidget())) {
      return;
    }
    renderDragWidgetItemIcon(graphics, client, itemManager, displayService, itemId, widgetItem);
  }

  private static void renderDragWidgetItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      int itemId,
      WidgetItem widgetItem) {
    SlotType slot = slotFromItemId(itemManager, itemId);
    if (slot == null) {
      return;
    }

    Rectangle dragBounds = widgetItem.getDraggingCanvasBounds();
    if (dragBounds == null) {
      dragBounds =
          resolveDragBounds(
              client, widgetItem.getWidget(), widgetItem.getCanvasBounds(false));
    }
    if (dragBounds != null) {
      drawTokenIcon(
          graphics,
          itemManager,
          displayService,
          slot,
          dragBounds,
          itemId,
          widgetItem.getQuantity(),
          true);
    }
  }

  public static boolean isItemBeingDragged(Client client, WidgetItem widgetItem) {
    return isDraggedWidgetItem(client, widgetItem);
  }

  private static boolean isDraggedWidgetItem(Client client, WidgetItem widgetItem) {
    if (widgetItem.getDraggingCanvasBounds() != null) {
      return true;
    }
    Widget draggedSlot = resolveDraggedSlotWidget(client);
    Widget itemWidget = widgetItem.getWidget();
    if (draggedSlot == null || itemWidget == null || draggedSlot != itemWidget) {
      return false;
    }
    Widget dragged = client.getDraggedWidget();
    if (dragged != null && isInventoryDragContainer(dragged)) {
      return hasVisualInventoryDragStarted(client, dragged);
    }
    if (dragged != null && isBankMainDragSource(dragged)) {
      return hasVisualBankDragStarted(client, dragged);
    }
    return true;
  }

  @Nullable
  private static Widget findItemChildAtCanvas(
      Client client, Widget container, int canvasX, int canvasY) {
    if (container == null) {
      return null;
    }
    Widget hit = findInChildrenAtCanvas(client, container.getDynamicChildren(), canvasX, canvasY);
    if (hit != null) {
      return hit;
    }
    return findInChildrenAtCanvas(client, container.getChildren(), canvasX, canvasY);
  }

  @Nullable
  private static Widget findInChildrenAtCanvas(
      Client client, Widget[] children, int canvasX, int canvasY) {
    if (children == null) {
      return null;
    }
    for (Widget child : children) {
      if (child == null || child.isHidden() || child.getItemId() <= 0) {
        continue;
      }
      Rectangle bounds = getWidgetCanvasBounds(client, child);
      if (bounds != null && bounds.contains(canvasX, canvasY)) {
        return child;
      }
    }
    return null;
  }

  @Nullable
  private static Widget resolveDraggedSlotWidget(Client client) {
    Widget dragged = client.getDraggedWidget();
    if (dragged == null) {
      return null;
    }
    int id = dragged.getId();
    if (id == InterfaceID.Inventory.ITEMS
        || id == InterfaceID.Bankside.ITEMS
        || id == InterfaceID.SharedBankSide.ITEMS
        || id == InterfaceID.Bankmain.ITEMS
        || id == InterfaceID.SharedBank.ITEMS
        || id == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS) {
      return dragged.getChild(dragged.getIndex());
    }
    return dragged;
  }

  public static void renderDraggedWidgetIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    if (client.getDraggedWidget() == null) {
      clearDragGrabOffset();
    }
    renderBankDragLayerIcons(graphics, client, itemManager, displayService);
  }

  public static boolean renderBankDragLayerIcons(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    boolean drew = false;
    for (int layerId : BANK_DRAG_LAYERS) {
      Widget layer = client.getWidget(layerId);
      if (layer == null || layer.isHidden()) {
        continue;
      }
      if (renderTokenChildrenOnLayer(graphics, client, itemManager, displayService, layer)) {
        drew = true;
      }
    }
    return drew;
  }

  public static void renderPressedInventoryItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    renderStationaryHoldCover(graphics, client, itemManager, displayService);
  }

  public static void renderPressedBankItemIcon(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService) {
    renderStationaryHoldCover(graphics, client, itemManager, displayService);
  }

  public static void drawTokenIcon(
      Graphics2D graphics,
      ItemManager itemManager,
      SlotDisplayService displayService,
      SlotType slot,
      Rectangle bounds,
      int itemId,
      int quantity) {
    drawTokenIcon(graphics, itemManager, displayService, slot, bounds, itemId, quantity, false);
  }

  public static void drawTokenIcon(
      Graphics2D graphics,
      ItemManager itemManager,
      SlotDisplayService displayService,
      SlotType slot,
      Rectangle bounds,
      int itemId,
      int quantity,
      boolean dragging) {
    if (bounds == null) {
      return;
    }
    boolean placeholder = BankItemUtils.isPlaceholderItem(itemManager, itemId, quantity);
    displayService.drawReplacementIcon(graphics, slot, bounds, TokenIconStyle.NORMAL);
    if (placeholder) {
      displayService.drawPlaceholderQuantity(graphics, bounds, quantity);
    } else {
      displayService.drawItemQuantity(graphics, bounds, quantity);
    }
  }

  private static boolean renderTokenChildrenOnLayer(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      Widget layer) {
    boolean drew = false;
    if (renderTokenChildren(graphics, client, itemManager, displayService, layer.getDynamicChildren())) {
      drew = true;
    }
    if (renderTokenChildren(graphics, client, itemManager, displayService, layer.getChildren())) {
      drew = true;
    }
    return drew;
  }

  private static boolean renderTokenChildren(
      Graphics2D graphics,
      Client client,
      ItemManager itemManager,
      SlotDisplayService displayService,
      Widget[] children) {
    if (children == null) {
      return false;
    }
    boolean drew = false;
    for (Widget child : children) {
      if (child == null || child.getItemId() <= 0) {
        continue;
      }
      SlotType slot = slotFromItemId(itemManager, child.getItemId());
      if (slot == null) {
        continue;
      }
      Rectangle bounds = getWidgetCanvasBounds(client, child);
      if (bounds == null) {
        bounds = resolveDragBounds(client, child, widgetSlotBounds(client, child));
      }
      if (bounds != null) {
        drawTokenIcon(
            graphics,
            itemManager,
            displayService,
            slot,
            bounds,
            child.getItemId(),
            child.getItemQuantity(),
            true);
        drew = true;
      }
    }
    return drew;
  }

  private static boolean isBankMainSourceWidget(@Nullable Widget widget) {
    if (widget == null) {
      return false;
    }
    Widget current = widget;
    while (current != null) {
      int id = current.getId();
      for (int containerId : BANK_MAIN_ITEM_CONTAINERS) {
        if (id == containerId) {
          return true;
        }
      }
      current = current.getParent();
    }
    return false;
  }

  private static boolean isInventoryDragContainer(Widget dragged) {
    int id = dragged.getId();
    return id == InterfaceID.Inventory.ITEMS
        || id == InterfaceID.Bankside.ITEMS
        || id == InterfaceID.SharedBankSide.ITEMS;
  }

  @Nullable
  private static Rectangle resolveActiveDragBounds(Client client, Widget draggedSlot) {
    return resolveDragBounds(client, draggedSlot, widgetSlotBounds(client, draggedSlot));
  }

  @Nullable
  private static Rectangle resolveDragBounds(
      Client client, @Nullable Widget slotWidget, @Nullable Rectangle slotBounds) {
    Point mouse = client.getMouseCanvasPosition();
    if (mouse == null) {
      return null;
    }

    if (slotBounds == null && slotWidget != null) {
      slotBounds = widgetSlotBounds(client, slotWidget);
    }

    int width = DRAG_ICON_SIZE;
    int height = DRAG_ICON_SIZE;
    if (slotBounds != null) {
      width = slotBounds.width;
      height = slotBounds.height;
    } else if (slotWidget != null && slotWidget.getWidth() > 0 && slotWidget.getHeight() > 0) {
      width = slotWidget.getWidth();
      height = slotWidget.getHeight();
    }

    Point grabOffset = getDragGrabOffset(client, slotBounds);
    return new Rectangle(
        mouse.getX() - grabOffset.getX(), mouse.getY() - grabOffset.getY(), width, height);
  }

  @Nullable
  private static Rectangle widgetSlotBounds(Client client, Widget slotWidget) {
    if (slotWidget == null) {
      return null;
    }
    Point location = slotWidget.getCanvasLocation();
    if (location != null && slotWidget.getWidth() > 0 && slotWidget.getHeight() > 0) {
      return new Rectangle(
          location.getX(), location.getY(), slotWidget.getWidth(), slotWidget.getHeight());
    }
    return getWidgetCanvasBounds(client, slotWidget);
  }

  private static Point getDragGrabOffset(Client client, @Nullable Rectangle slotBounds) {
    Widget dragged = client.getDraggedWidget();
    if (dragged == null) {
      clearDragGrabOffset();
      return new Point(0, 0);
    }

    int sourceId = dragged.getId() << 16 | dragged.getIndex();
    if (dragGrabOffset == null || dragGrabSourceId != sourceId) {
      Point mouse = client.getMouseCanvasPosition();
      if (mouse != null && slotBounds != null) {
        dragGrabOffset = new Point(mouse.getX() - slotBounds.x, mouse.getY() - slotBounds.y);
      } else {
        dragGrabOffset = new Point(0, 0);
      }
      dragGrabSourceId = sourceId;
    }

    return dragGrabOffset;
  }

  private static void clearDragGrabOffset() {
    dragGrabOffset = null;
    dragGrabSourceId = -1;
  }

  private static void clearInventoryHoldAnchor() {
    inventoryHoldAnchorMouse = null;
    inventoryHoldAnchorSourceId = -1;
  }

  private static void clearBankHoldAnchor() {
    bankHoldAnchorMouse = null;
    bankHoldAnchorSourceId = -1;
  }

  private static boolean hasVisualInventoryDragStarted(Client client, Widget dragged) {
    if (!isInventoryDragContainer(dragged)) {
      return false;
    }
    Point mouse = client.getMouseCanvasPosition();
    if (mouse == null) {
      return false;
    }
    int sourceId = dragged.getId() << 16 | dragged.getIndex();
    if (inventoryHoldAnchorMouse == null || inventoryHoldAnchorSourceId != sourceId) {
      inventoryHoldAnchorMouse = new Point(mouse.getX(), mouse.getY());
      inventoryHoldAnchorSourceId = sourceId;
      return false;
    }
    int dx = mouse.getX() - inventoryHoldAnchorMouse.getX();
    int dy = mouse.getY() - inventoryHoldAnchorMouse.getY();
    return (dx * dx + dy * dy) > DRAG_MOVE_THRESHOLD_SQ;
  }

  private static boolean hasVisualBankDragStarted(Client client, Widget dragged) {
    if (!isBankMainDragSource(dragged)) {
      return false;
    }
    Point mouse = client.getMouseCanvasPosition();
    if (mouse == null) {
      return false;
    }
    int sourceId = dragged.getId() << 16 | dragged.getIndex();
    if (bankHoldAnchorMouse == null || bankHoldAnchorSourceId != sourceId) {
      bankHoldAnchorMouse = new Point(mouse.getX(), mouse.getY());
      bankHoldAnchorSourceId = sourceId;
      return false;
    }
    int dx = mouse.getX() - bankHoldAnchorMouse.getX();
    int dy = mouse.getY() - bankHoldAnchorMouse.getY();
    return (dx * dx + dy * dy) > DRAG_MOVE_THRESHOLD_SQ;
  }

  private static boolean isBankMainDragSource(Widget dragged) {
    if (isBankDragContainer(dragged)) {
      return true;
    }
    return isBankMainSourceWidget(dragged);
  }

  private static boolean isBankDragContainer(Widget dragged) {
    int id = dragged.getId();
    return id == InterfaceID.Bankmain.ITEMS
        || id == InterfaceID.SharedBank.ITEMS
        || id == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS;
  }

  @Nullable
  private static Rectangle getWidgetCanvasBounds(Client client, Widget widget) {
    Point location = widget.getCanvasLocation();
    if (location != null && widget.getWidth() > 0 && widget.getHeight() > 0) {
      return new Rectangle(location.getX(), location.getY(), widget.getWidth(), widget.getHeight());
    }

    Rectangle bounds = widget.getBounds();
    if (bounds != null && bounds.width > 0 && bounds.height > 0) {
      return new Rectangle(
          (int) bounds.getX(), (int) bounds.getY(), bounds.width, bounds.height);
    }

    if (TokenItemWidgetScopes.isTokenItemDragWidget(widget)) {
      return null;
    }

    return null;
  }

  @Nullable
  private static SlotType slotFromItemId(ItemManager itemManager, int itemId) {
    return BankItemUtils.resolveSlotType(itemManager, itemId);
  }
}
