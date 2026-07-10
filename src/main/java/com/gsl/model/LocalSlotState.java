package com.gsl.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;

@Value
public class LocalSlotState {
  Set<SlotType> tokensPresent;
  Set<SlotType> tokensInGroupStorage;
  Set<SlotType> equippedSlots;
  Map<SlotType, Integer> equippedItemIds;
  int heldTokenCount;

  public static LocalSlotState empty() {
    return new LocalSlotState(
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptyMap(),
        0);
  }

  public boolean hasToken(SlotType slot) {
    return tokensPresent.contains(slot);
  }

  public boolean hasTokenInGroupStorage(SlotType slot) {
    return tokensInGroupStorage.contains(slot);
  }

  public boolean isEquipped(SlotType slot) {
    return equippedSlots.contains(slot);
  }

  public int getEquippedItemId(SlotType slot) {
    return equippedItemIds.getOrDefault(slot, -1);
  }

  public Set<SlotType> getTokensPresent() {
    return Collections.unmodifiableSet(tokensPresent);
  }

  public Set<SlotType> getTokensInGroupStorage() {
    return Collections.unmodifiableSet(tokensInGroupStorage);
  }

  public Set<SlotType> getEquippedSlots() {
    return Collections.unmodifiableSet(equippedSlots);
  }

  public Map<SlotType, Integer> getEquippedItemIds() {
    return Collections.unmodifiableMap(equippedItemIds);
  }

  public static LocalSlotState of(
      Set<SlotType> tokensPresent,
      Set<SlotType> tokensInGroupStorage,
      Set<SlotType> equippedSlots,
      Map<SlotType, Integer> equippedItemIds,
      int heldTokenCount) {
    return new LocalSlotState(
        copySlotSet(tokensPresent),
        copySlotSet(tokensInGroupStorage),
        copySlotSet(equippedSlots),
        equippedItemIds == null || equippedItemIds.isEmpty()
            ? Collections.emptyMap()
            : new EnumMap<>(equippedItemIds),
        heldTokenCount);
  }

  public static LocalSlotState of(
      Set<SlotType> tokensPresent, Set<SlotType> equippedSlots, int heldTokenCount) {
    return of(
        tokensPresent,
        Collections.emptySet(),
        equippedSlots,
        Collections.emptyMap(),
        heldTokenCount);
  }

  private static EnumSet<SlotType> copySlotSet(Set<SlotType> slots) {
    if (slots == null || slots.isEmpty()) {
      return EnumSet.noneOf(SlotType.class);
    }
    return EnumSet.copyOf(slots);
  }
}
