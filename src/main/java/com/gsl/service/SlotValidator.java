package com.gsl.service;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import com.gsl.model.ValidationResult;
import com.gsl.model.Violation;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

@Singleton
public class SlotValidator {
  private final GroupSlotLockedConfig config;
  private final ItemManager itemManager;

  @Inject
  SlotValidator(GroupSlotLockedConfig config, ItemManager itemManager) {
    this.config = config;
    this.itemManager = itemManager;
  }

  public boolean hasActiveClaim(LocalSlotState state, SlotType slot) {
    return state.getHeldTokenCount() <= config.maxHeldTokens() && state.hasToken(slot);
  }

  public ValidationResult validateEquip(LocalSlotState state, int itemId) {
    return validateEquip(state, slotsForItem(itemId), config.maxHeldTokens(), config.maxEquipped());
  }

  public static ValidationResult validateEquip(
      LocalSlotState state, Set<SlotType> itemSlots, int maxHeldTokens, int maxEquipped) {
    if (itemSlots.isEmpty()) {
      return ValidationResult.ok();
    }
    if (state.getHeldTokenCount() > maxHeldTokens) {
      return ValidationResult.fail(Violation.TOO_MANY_TOKENS, null);
    }
    for (SlotType slot : itemSlots) {
      if (!state.hasToken(slot)) {
        return ValidationResult.fail(Violation.NO_SLOT_CLAIM, slot);
      }
    }
    Set<SlotType> afterEquip = simulateEquip(state.getEquippedSlots(), itemSlots);
    if (afterEquip.size() > maxEquipped) {
      return ValidationResult.fail(Violation.OVER_EQUIP_LIMIT, null);
    }
    return ValidationResult.ok();
  }

  public Violation getViolationForItem(LocalSlotState state, int itemId) {
    if (itemId <= 0 || isPlaceholderItem(itemId)) {
      return Violation.NONE;
    }
    int canonicalId = itemManager.canonicalize(itemId);
    if (SlotType.isTokenItem(canonicalId)) {
      return Violation.NONE;
    }
    return validateEquip(state, canonicalId).getViolation();
  }

  private boolean isPlaceholderItem(int itemId) {
    return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
  }

  public List<Violation> getCurrentViolations(LocalSlotState state) {
    List<Violation> violations = new ArrayList<>();
    if (state.getHeldTokenCount() > config.maxHeldTokens()) {
      violations.add(Violation.TOO_MANY_TOKENS);
    }
    if (state.getEquippedSlots().size() > config.maxEquipped()) {
      violations.add(Violation.OVER_EQUIP_LIMIT);
    }
    for (SlotType equipped : state.getEquippedSlots()) {
      if (!hasActiveClaim(state, equipped)) {
        violations.add(Violation.NO_SLOT_CLAIM);
        break;
      }
    }
    if (!violations.isEmpty() && !state.getEquippedSlots().isEmpty()) {
      violations.add(Violation.CURRENTLY_ILLEGAL);
    }
    return violations;
  }

  public boolean isLoadoutIllegal(LocalSlotState state) {
    return !getCurrentViolations(state).isEmpty();
  }

  public Set<SlotType> slotsForItem(int itemId) {
    ItemStats stats = itemManager.getItemStats(itemId);
    if (stats == null || !stats.isEquipable()) {
      return EnumSet.noneOf(SlotType.class);
    }
    ItemEquipmentStats equipment = stats.getEquipment();
    if (equipment == null) {
      return EnumSet.noneOf(SlotType.class);
    }
    EnumSet<SlotType> slots = EnumSet.noneOf(SlotType.class);
    SlotType.fromEquipmentIndex(equipment.getSlot()).ifPresent(slots::add);
    if (equipment.isTwoHanded()) {
      slots.add(SlotType.MAIN_HAND);
      slots.add(SlotType.OFF_HAND);
    }
    return slots;
  }

  public static Set<SlotType> simulateEquip(
      Set<SlotType> currentlyEquipped, Set<SlotType> newItemSlots) {
    EnumSet<SlotType> result =
        currentlyEquipped == null || currentlyEquipped.isEmpty()
            ? EnumSet.noneOf(SlotType.class)
            : EnumSet.copyOf(currentlyEquipped);
    if (newItemSlots.contains(SlotType.MAIN_HAND) && newItemSlots.contains(SlotType.OFF_HAND)) {
      result.remove(SlotType.MAIN_HAND);
      result.remove(SlotType.OFF_HAND);
    } else if (newItemSlots.contains(SlotType.MAIN_HAND)) {
      result.remove(SlotType.MAIN_HAND);
      if (currentlyEquipped.contains(SlotType.MAIN_HAND)
          && currentlyEquipped.contains(SlotType.OFF_HAND)) {
        result.remove(SlotType.OFF_HAND);
      }
    } else if (newItemSlots.contains(SlotType.OFF_HAND)) {
      result.remove(SlotType.OFF_HAND);
    }
    for (SlotType slot : newItemSlots) {
      result.remove(slot);
    }
    result.addAll(newItemSlots);
    return result;
  }
}
