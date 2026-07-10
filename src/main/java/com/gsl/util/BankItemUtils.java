package com.gsl.util;

import com.gsl.model.SlotType;
import javax.annotation.Nullable;
import net.runelite.client.game.ItemManager;

public final class BankItemUtils {
  private BankItemUtils() {}

  public static boolean isPlaceholderItem(ItemManager itemManager, int itemId, int quantity) {
    if (itemId <= 0) {
      return false;
    }
    if (quantity <= 0) {
      return true;
    }
    return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
  }

  @Nullable
  public static SlotType resolveSlotType(ItemManager itemManager, int itemId) {
    if (itemId <= 0) {
      return null;
    }
    SlotType slot = SlotType.fromTokenItemId(itemManager.canonicalize(itemId));
    if (slot != null) {
      return slot;
    }
    int templateId = itemManager.getItemComposition(itemId).getPlaceholderTemplateId();
    if (templateId != -1) {
      return SlotType.fromTokenItemId(itemManager.canonicalize(templateId));
    }
    return null;
  }
}
