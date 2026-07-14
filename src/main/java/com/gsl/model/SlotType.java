package com.gsl.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.gameval.ItemID;

@Getter
public enum SlotType {
  HEAD(EquipmentInventorySlot.HEAD, ItemID.WILDERNESS_CAPE_6, "Head", "H"),
  CAPE(EquipmentInventorySlot.CAPE, ItemID.WILDERNESS_CAPE_16, "Cape", "C"),
  NECK(EquipmentInventorySlot.AMULET, ItemID.WILDERNESS_CAPE_26, "Neck", "N"),
  AMMO(EquipmentInventorySlot.AMMO, ItemID.WILDERNESS_CAPE_36, "Ammo", "A"),
  BODY(EquipmentInventorySlot.BODY, ItemID.WILDERNESS_CAPE_46, "Body", "B"),
  LEGS(EquipmentInventorySlot.LEGS, ItemID.WILDERNESS_CAPE_10, "Legs", "L"),
  MAIN_HAND(EquipmentInventorySlot.WEAPON, ItemID.WILDERNESS_CAPE_20, "Main hand", "MH"),
  OFF_HAND(EquipmentInventorySlot.SHIELD, ItemID.WILDERNESS_CAPE_30, "Off hand", "OH"),
  BOOTS(EquipmentInventorySlot.BOOTS, ItemID.WILDERNESS_CAPE_40, "Boots", "Bo"),
  RING(EquipmentInventorySlot.RING, ItemID.WILDERNESS_CAPE_50, "Ring", "Ri"),
  GLOVES(EquipmentInventorySlot.GLOVES, ItemID.WILDERNESS_CAPE_2, "Gloves", "Gl");
  private static final Map<Integer, SlotType> BY_TOKEN_ID =
      Arrays.stream(values())
          .collect(Collectors.toMap(SlotType::getTokenItemId, Function.identity()));
  private static final Map<EquipmentInventorySlot, SlotType> BY_EQUIPMENT_SLOT =
      Arrays.stream(values())
          .collect(Collectors.toMap(SlotType::getEquipmentSlot, Function.identity()));
  private final EquipmentInventorySlot equipmentSlot;
  private final int tokenItemId;
  private final String defaultDisplayName;
  private final String defaultAbbrev;

  SlotType(
      EquipmentInventorySlot equipmentSlot,
      int tokenItemId,
      String defaultDisplayName,
      String defaultAbbrev) {
    this.equipmentSlot = equipmentSlot;
    this.tokenItemId = tokenItemId;
    this.defaultDisplayName = defaultDisplayName;
    this.defaultAbbrev = defaultAbbrev;
  }

  public static boolean isTokenItem(int itemId) {
    return BY_TOKEN_ID.containsKey(itemId);
  }

  public static SlotType fromTokenItemId(int itemId) {
    return BY_TOKEN_ID.get(itemId);
  }

  public static Optional<SlotType> fromEquipmentSlot(EquipmentInventorySlot slot) {
    return Optional.ofNullable(BY_EQUIPMENT_SLOT.get(slot));
  }

  public static Optional<SlotType> fromEquipmentIndex(int slotIndex) {
    for (EquipmentInventorySlot equipmentSlot : EquipmentInventorySlot.values()) {
      if (equipmentSlot.getSlotIdx() == slotIndex) {
        return fromEquipmentSlot(equipmentSlot);
      }
    }
    return Optional.empty();
  }

  public String fileName() {
    return name().toLowerCase() + ".png";
  }
}
