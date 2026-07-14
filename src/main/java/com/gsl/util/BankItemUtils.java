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
    if (quantity <= 0 || quantity == Integer.MAX_VALUE) {
      return true;
    }
    return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
  }

  /**
   * Bank placeholders often store quantity 1 internally while the UI shows 0. Bank tag layout
   * slots with no matching bank item use the real (non-placeholder) item ID but set quantity to
   * {@link Integer#MAX_VALUE} with {@code ItemQuantityMode.NEVER} as a sentinel meaning "no
   * quantity" (see LayoutManager#drawItem in RuneLite).
   */
  public static int resolvePlaceholderDisplayQuantity(
      ItemManager itemManager, int itemId, int quantity) {
    if (itemId <= 0) {
      return quantity;
    }
    if (quantity <= 0 || quantity == Integer.MAX_VALUE) {
      return 0;
    }
    if (itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1) {
      return 0;
    }
    return quantity;
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
