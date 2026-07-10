package com.gsl.util;

import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;

public final class TokenItemWidgetScopes {
  private TokenItemWidgetScopes() {}

  public static boolean isTokenItemMenuContext(MenuEntry entry) {
    if (entry == null) {
      return false;
    }
    if (isTokenItemComponent(entry.getParam1())) {
      return true;
    }
    return isTokenItemWidget(entry.getWidget());
  }

  /**
   * Player inventory panel while the bank is open (deposit actions).
   */
  public static boolean isTokenBankDepositContext(MenuEntry entry) {
    int componentId = resolveComponentId(entry);
    return componentId == InterfaceID.Bankside.ITEMS
        || componentId == InterfaceID.SharedBankSide.ITEMS;
  }

  /**
   * Bank item grid (withdraw actions).
   */
  public static boolean isTokenBankWithdrawContext(MenuEntry entry) {
    int componentId = resolveComponentId(entry);
    return componentId == InterfaceID.Bankmain.ITEMS
        || componentId == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS
        || componentId == InterfaceID.SharedBank.ITEMS
        || componentId == InterfaceID.SharedBank.MAIN_BANK;
  }

  public static boolean isTokenBankActionContext(MenuEntry entry) {
    return isTokenBankDepositContext(entry) || isTokenBankWithdrawContext(entry);
  }

  /**
   * Contexts where token left-click should be overridden (e.g. promote Examine). Bank withdraw/deposit
   * and similar interface actions are excluded so vanilla left-click behavior is preserved.
   */
  public static boolean isTokenLeftClickReorderContext(MenuEntry entry) {
    int componentId = resolveComponentId(entry);
    return componentId == InterfaceID.Inventory.ITEMS;
  }

  private static int resolveComponentId(MenuEntry entry) {
    if (entry.getParam1() > 0) {
      return entry.getParam1();
    }
    Widget widget = entry.getWidget();
    return widget != null ? widget.getId() : -1;
  }

  public static boolean isTokenItemWidget(Widget widget) {
    if (widget == null) {
      return false;
    }
    return isTokenItemInterface(WidgetUtil.componentToInterface(widget.getId()));
  }

  public static boolean isTokenItemInterface(int groupId) {
    return groupId == InterfaceID.INVENTORY
        || groupId == InterfaceID.BANKSIDE
        || groupId == InterfaceID.BANKMAIN
        || groupId == InterfaceID.BANK_DEPOSITBOX
        || groupId == InterfaceID.EQUIPMENT
        || groupId == InterfaceID.EQUIPMENT_SIDE
        || groupId == InterfaceID.WORNITEMS
        || groupId == InterfaceID.SHARED_BANK
        || groupId == InterfaceID.SHARED_BANK_SIDE;
  }

  public static boolean isTokenItemComponent(int componentId) {
    if (componentId <= 0) {
      return false;
    }
    return componentId == InterfaceID.Inventory.ITEMS
        || componentId == InterfaceID.Bankside.ITEMS
        || componentId == InterfaceID.Bankmain.ITEMS
        || componentId == InterfaceID.Bankmain.BANKTAGS_DISPLAY_ITEMS
        || componentId == InterfaceID.Bankmain.BANKTAGS_DISPLAY_DRAGLAYER
        || componentId == InterfaceID.EquipmentSide.ITEMS
        || componentId == InterfaceID.SharedBank.ITEMS
        || componentId == InterfaceID.SharedBank.MAIN_BANK
        || componentId == InterfaceID.SharedBankSide.ITEMS
        || isTokenItemInterface(WidgetUtil.componentToInterface(componentId));
  }

  public static boolean isTokenItemDragWidget(Widget widget) {
    if (widget == null) {
      return false;
    }
    Widget current = widget;
    while (current != null) {
      if (isTokenItemComponent(current.getId())) {
        return true;
      }
      current = current.getParent();
    }
    return false;
  }
}
