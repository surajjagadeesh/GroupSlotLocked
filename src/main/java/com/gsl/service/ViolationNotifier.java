package com.gsl.service;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import com.gsl.model.Violation;
import java.awt.Color;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.ColorUtil;

@Singleton
public class ViolationNotifier {
  private final GroupSlotLockedConfig config;
  private final SlotValidator slotValidator;
  private final SlotDisplayService displayService;
  private final ChatMessageManager chatMessageManager;
  private boolean wasIllegal;
  private boolean wasOverTokenCap;

  @Inject
  ViolationNotifier(
      GroupSlotLockedConfig config,
      SlotValidator slotValidator,
      SlotDisplayService displayService,
      ChatMessageManager chatMessageManager) {
    this.config = config;
    this.slotValidator = slotValidator;
    this.displayService = displayService;
    this.chatMessageManager = chatMessageManager;
  }

  public void onStateChanged(LocalSlotState state) {
    if (!config.chatWarnings()) {
      wasIllegal = slotValidator.isLoadoutIllegal(state);
      wasOverTokenCap = state.getHeldTokenCount() > config.maxHeldTokens();
      return;
    }
    boolean illegal = slotValidator.isLoadoutIllegal(state);
    boolean overTokenCap = state.getHeldTokenCount() > config.maxHeldTokens();
    if (overTokenCap && !wasOverTokenCap) {
      queueMessage(
          "Group Slot Locked: "
              + state.getHeldTokenCount()
              + "/"
              + config.maxHeldTokens()
              + " tokens held — store one in group storage.");
    }
    if (illegal && !wasIllegal) {
      queueMessage(buildIllegalMessage(state));
    }
    wasIllegal = illegal;
    wasOverTokenCap = overTokenCap;
  }

  public void reset() {
    wasIllegal = false;
    wasOverTokenCap = false;
  }

  private String buildIllegalMessage(LocalSlotState state) {
    for (Violation violation : slotValidator.getCurrentViolations(state)) {
      switch (violation) {
        case TOO_MANY_TOKENS:
          return "Group Slot Locked: illegal loadout — too many tokens held.";
        case OVER_EQUIP_LIMIT:
          return "Group Slot Locked: illegal loadout — too many equipment slots filled.";
        case NO_SLOT_CLAIM:
          return buildMissingClaimMessage(state);
        default:
          break;
      }
    }
    return "Group Slot Locked: illegal loadout — unequip restricted gear.";
  }

  private String buildMissingClaimMessage(LocalSlotState state) {
    SlotType missing = slotValidator.findMissingClaimSlot(state).orElse(null);
    if (missing == null) {
      return "Group Slot Locked: illegal loadout — missing a slot token.";
    }
    int mainHandId = state.getEquippedItemId(SlotType.MAIN_HAND);
    int offHandId = state.getEquippedItemId(SlotType.OFF_HAND);
    if (missing == SlotType.OFF_HAND && mainHandId > 0 && mainHandId == offHandId) {
      return "Group Slot Locked: illegal loadout — two-handed weapon requires both the "
          + displayService.getDisplayName(SlotType.MAIN_HAND)
          + " and "
          + displayService.getDisplayName(SlotType.OFF_HAND)
          + " tokens; missing the "
          + displayService.getDisplayName(SlotType.OFF_HAND)
          + " token.";
    }
    return "Group Slot Locked: illegal loadout — missing the "
        + displayService.getDisplayName(missing)
        + " slot token.";
  }

  private void queueMessage(String message) {
    chatMessageManager.queue(
        QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(ColorUtil.wrapWithColorTag(message, Color.RED))
            .build());
  }
}
