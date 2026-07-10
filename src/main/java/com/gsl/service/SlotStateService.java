package com.gsl.service;

import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;

@Singleton
public class SlotStateService {
  private final Client client;
  private final ItemManager itemManager;
  @Getter private volatile LocalSlotState state = LocalSlotState.empty();
  private EnumSet<SlotType> lastKnownBankTokens = EnumSet.noneOf(SlotType.class);
  private int lastKnownBankTokenCount = 0;
  private final List<Consumer<LocalSlotState>> listeners = new ArrayList<>();

  @Inject
  SlotStateService(Client client, ItemManager itemManager) {
    this.client = client;
    this.itemManager = itemManager;
  }

  public void addListener(Consumer<LocalSlotState> listener) {
    listeners.add(listener);
  }

  public void removeListener(Consumer<LocalSlotState> listener) {
    listeners.remove(listener);
  }

  public void refreshAll() {
    refreshInventoryAndWorn();
    refreshBankIfAvailable();
    refreshGroupStorage();
  }

  public void refreshInventoryAndWorn() {
    EnumSet<SlotType> tokensPresent = EnumSet.noneOf(SlotType.class);
    int heldTokenCount = 0;
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    heldTokenCount += collectTokens(inventory, tokensPresent);
    ItemContainer bank = client.getItemContainer(InventoryID.BANK);
    if (bank != null) {
      EnumSet<SlotType> bankTokens = EnumSet.noneOf(SlotType.class);
      int bankCount = collectTokens(bank, bankTokens);
      heldTokenCount += bankCount;
      tokensPresent.addAll(bankTokens);
      lastKnownBankTokens = bankTokens;
      lastKnownBankTokenCount = bankCount;
    } else {
      heldTokenCount += lastKnownBankTokenCount;
      tokensPresent.addAll(lastKnownBankTokens);
    }
    EnumSet<SlotType> equippedSlots = EnumSet.noneOf(SlotType.class);
    EnumMap<SlotType, Integer> equippedItemIds = new EnumMap<>(SlotType.class);
    ItemContainer worn = client.getItemContainer(InventoryID.WORN);
    if (worn != null) {
      for (SlotType slot : SlotType.values()) {
        Item item = worn.getItem(slot.getEquipmentSlot().getSlotIdx());
        if (item != null && item.getId() > 0) {
          equippedSlots.add(slot);
          equippedItemIds.put(slot, itemManager.canonicalize(item.getId()));
        }
      }
    }
    updateState(
        LocalSlotState.of(
            tokensPresent,
            state.getTokensInGroupStorage(),
            equippedSlots,
            equippedItemIds,
            heldTokenCount));
  }

  public void refreshBankIfAvailable() {
    ItemContainer bank = client.getItemContainer(InventoryID.BANK);
    if (bank == null) {
      return;
    }
    EnumSet<SlotType> tokensPresent = EnumSet.noneOf(SlotType.class);
    int heldTokenCount = 0;
    ItemContainer inventory = client.getItemContainer(InventoryID.INV);
    heldTokenCount += collectTokens(inventory, tokensPresent);
    EnumSet<SlotType> bankTokens = EnumSet.noneOf(SlotType.class);
    int bankCount = collectTokens(bank, bankTokens);
    heldTokenCount += bankCount;
    tokensPresent.addAll(bankTokens);
    lastKnownBankTokens = bankTokens;
    lastKnownBankTokenCount = bankCount;
    updateState(
        LocalSlotState.of(
            tokensPresent,
            state.getTokensInGroupStorage(),
            state.getEquippedSlots(),
            state.getEquippedItemIds(),
            heldTokenCount));
  }

  public void refreshGroupStorage() {
    EnumSet<SlotType> groupStorageTokens = EnumSet.noneOf(SlotType.class);
    collectTokenTypes(client.getItemContainer(InventoryID.INV_GROUP_TEMP), groupStorageTokens);
    if (groupStorageTokens.equals(state.getTokensInGroupStorage())) {
      return;
    }
    updateState(
        LocalSlotState.of(
            state.getTokensPresent(),
            groupStorageTokens,
            state.getEquippedSlots(),
            state.getEquippedItemIds(),
            state.getHeldTokenCount()));
  }

  private int collectTokens(ItemContainer container, EnumSet<SlotType> tokensPresent) {
    return collectTokenTypes(container, tokensPresent);
  }

  private int collectTokenTypes(ItemContainer container, EnumSet<SlotType> tokensPresent) {
    if (container == null) {
      return 0;
    }
    boolean bankContainer = container.getId() == InventoryID.BANK;
    int count = 0;
    for (Item item : container.getItems()) {
      if (item == null || item.getId() <= 0) {
        continue;
      }
      if (bankContainer && isBankPlaceholder(item)) {
        continue;
      }
      int canonicalId = itemManager.canonicalize(item.getId());
      SlotType slot = SlotType.fromTokenItemId(canonicalId);
      if (slot != null) {
        tokensPresent.add(slot);
        count += item.getQuantity();
      }
    }
    return count;
  }

  private boolean isBankPlaceholder(Item item) {
    if (item.getQuantity() <= 0) {
      return true;
    }
    return itemManager.getItemComposition(item.getId()).getPlaceholderTemplateId() != -1;
  }

  private void updateState(LocalSlotState newState) {
    if (state.getTokensPresent().equals(newState.getTokensPresent())
        && state.getTokensInGroupStorage().equals(newState.getTokensInGroupStorage())
        && state.getEquippedSlots().equals(newState.getEquippedSlots())
        && state.getEquippedItemIds().equals(newState.getEquippedItemIds())
        && state.getHeldTokenCount() == newState.getHeldTokenCount()) {
      return;
    }
    state = newState;
    for (Consumer<LocalSlotState> listener : listeners) {
      listener.accept(state);
    }
  }
}
