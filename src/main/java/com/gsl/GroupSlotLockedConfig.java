package com.gsl;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("group-slot-locked")
public interface GroupSlotLockedConfig extends Config {
  @ConfigItem(
      keyName = "penaltyOverlay",
      name = "Penalty overlay",
      description =
          "Black out the screen (except inventory/equipment) when wearing an illegal loadout")
  default boolean penaltyOverlay() {
    return true;
  }

  @ConfigItem(
      keyName = "highlightRestricted",
      name = "Highlight restricted items",
      description = "Draw a red overlay on items that cannot be equipped")
  default boolean highlightRestricted() {
    return true;
  }

  @ConfigItem(
      keyName = "maxHeldTokens",
      name = "Max held tokens",
      description = "Maximum slot token items allowed in personal bank + inventory")
  default int maxHeldTokens() {
    return 5;
  }

  @ConfigItem(
      keyName = "maxEquipped",
      name = "Max equipped slots",
      description = "Maximum tracked equipment slots that may be filled at once")
  default int maxEquipped() {
    return 5;
  }

  @ConfigItem(
      keyName = "showTokenBadge",
      name = "Replace token icons",
      description = "Replace team cape sprites with slot icons in inventory, bank, and equipment")
  default boolean replaceTokenIcons() {
    return true;
  }

  @ConfigItem(
      keyName = "showEquipmentIndicators",
      name = "Equipment indicators",
      description = "Show a check/cross badge on each Worn Equipment tab slot for token status")
  default boolean showEquipmentIndicators() {
    return true;
  }

  @ConfigItem(
      keyName = "bankRefreshInterval",
      name = "Bank refresh interval",
      description = "Game ticks between bank rescans when the bank container is loaded")
  default int bankRefreshInterval() {
    return 10;
  }

  @ConfigItem(
      keyName = "chatWarnings",
      name = "Chat warnings",
      description =
          "Show chat messages when entering an illegal loadout or exceeding the token cap")
  default boolean chatWarnings() {
    return true;
  }
}
