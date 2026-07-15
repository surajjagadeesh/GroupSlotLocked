package com.gsl.service;

import com.gsl.model.SlotType;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.PostItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Overrides the inventory model backing each slot token item with the (unused) ScapeRune
 * teleport tablet model. The icon-replacement overlay draws over these items every frame, but if
 * it ever fails to run for a particular widget/context, the fallback visual is an unrelated
 * teleport tablet instead of an obvious team cape.
 *
 * <p>{@link net.runelite.api.NodeCache} only exposes a blanket {@code reset()} — there's no public
 * API to evict a single item id from the client's composition cache. So instead of resetting the
 * whole cache (which would also discard any other plugin's unrelated composition overrides), we
 * remember the original model for exactly the item ids we touch and set it back on shutdown.
 */
@Slf4j
@Singleton
public class TokenModelOverrideService {
  private final Client client;
  private final Map<Integer, Integer> originalModelByItemId = new HashMap<>();
  private int tabletModelId = -1;

  @Inject
  TokenModelOverrideService(Client client) {
    this.client = client;
  }

  /** Must be called on the client thread. Applies the override to any already-cached compositions. */
  public void warmUp() {
    int modelId = resolveTabletModelId();
    if (modelId <= 0) {
      return;
    }
    boolean changed = false;
    for (SlotType slot : SlotType.values()) {
      ItemComposition composition = client.getItemDefinition(slot.getTokenItemId());
      changed |= applyOverride(composition, modelId);
      int placeholderId = composition.getPlaceholderId();
      if (placeholderId > 0 && placeholderId != slot.getTokenItemId()) {
        changed |= applyOverride(client.getItemDefinition(placeholderId), modelId);
      }
    }
    if (changed) {
      flushCaches();
    }
  }

  /** Must be called on the client thread. Puts back the original model for any item we overrode. */
  public void restore() {
    if (originalModelByItemId.isEmpty()) {
      return;
    }
    boolean changed = false;
    for (Map.Entry<Integer, Integer> entry : originalModelByItemId.entrySet()) {
      ItemComposition composition = client.getItemDefinition(entry.getKey());
      if (composition.getInventoryModel() != entry.getValue()) {
        composition.setInventoryModel(entry.getValue());
        changed = true;
      }
    }
    originalModelByItemId.clear();
    if (changed) {
      flushCaches();
    }
  }

  @Subscribe
  public void onPostItemComposition(PostItemComposition event) {
    ItemComposition composition = event.getItemComposition();
    if (!isTokenComposition(composition)) {
      return;
    }
    int modelId = resolveTabletModelId();
    if (modelId <= 0) {
      return;
    }
    if (applyOverride(composition, modelId)) {
      flushCaches();
    }
  }

  private boolean isTokenComposition(ItemComposition composition) {
    if (SlotType.isTokenItem(composition.getId())) {
      return true;
    }
    int templateId = composition.getPlaceholderTemplateId();
    return templateId != -1 && SlotType.isTokenItem(composition.getPlaceholderId());
  }

  private boolean applyOverride(ItemComposition composition, int modelId) {
    if (composition.getInventoryModel() == modelId) {
      return false;
    }
    originalModelByItemId.putIfAbsent(composition.getId(), composition.getInventoryModel());
    composition.setInventoryModel(modelId);
    return true;
  }

  private int resolveTabletModelId() {
    if (tabletModelId > 0) {
      return tabletModelId;
    }
    ItemComposition tablet = client.getItemDefinition(ItemID.XMAS19_TABLET_SCAPE_RUNE);
    tabletModelId = tablet.getInventoryModel();
    if (tabletModelId <= 0) {
      log.debug("Could not resolve ScapeRune tablet inventory model");
    }
    return tabletModelId;
  }

  private void flushCaches() {
    client.getItemModelCache().reset();
    client.getItemSpriteCache().reset();
  }
}
