package com.gsl.service;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.LocalSlotState;
import com.gsl.model.Violation;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

@Singleton
public class ViolationNotifier {
  private final GroupSlotLockedConfig config;
  private final SlotValidator slotValidator;
  private final ChatMessageManager chatMessageManager;
  private boolean wasIllegal;
  private boolean wasOverTokenCap;

  @Inject
  ViolationNotifier(
      GroupSlotLockedConfig config,
      SlotValidator slotValidator,
      ChatMessageManager chatMessageManager) {
    this.config = config;
    this.slotValidator = slotValidator;
    this.chatMessageManager = chatMessageManager;
  }

  public void onStateChanged(LocalSlotState state) {
    if (!config.enablePlugin() || !config.chatWarnings()) {
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
          return "Group Slot Locked: illegal loadout — missing slot token claim.";
        default:
          break;
      }
    }
    return "Group Slot Locked: illegal loadout — unequip restricted gear.";
  }

  private void queueMessage(String message) {
    chatMessageManager.queue(
        QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(message)
            .build());
  }
}
