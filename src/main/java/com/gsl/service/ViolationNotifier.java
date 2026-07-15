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
    // Only treat the loadout as "illegal" (for chat purposes) when something is actually
    // equipped illegally — TOO_MANY_TOKENS alone (e.g. holding an extra token with nothing
    // worn) is already fully covered by the over-cap message below, and would otherwise fire
    // both every time.
    boolean illegal =
        slotValidator.getCurrentViolations(state).contains(Violation.CURRENTLY_ILLEGAL);
    boolean overTokenCap = state.getHeldTokenCount() > config.maxHeldTokens();
    if (!config.chatWarnings()) {
      wasIllegal = illegal;
      wasOverTokenCap = overTokenCap;
      return;
    }
    if (overTokenCap && !wasOverTokenCap) {
      queueMessage(
          "You're holding "
              + state.getHeldTokenCount()
              + " slot tokens, but you can only hold "
              + config.maxHeldTokens()
              + ". Store one in group storage.");
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
          return "You're holding too many slot tokens.";
        case OVER_EQUIP_LIMIT:
          return "You have too many items equipped.";
        case NO_SLOT_CLAIM:
          return buildMissingClaimMessage(state);
        default:
          break;
      }
    }
    return "You're wearing illegal equipment. Unequip the restricted gear.";
  }

  private String buildMissingClaimMessage(LocalSlotState state) {
    SlotType missing = slotValidator.findMissingClaimSlot(state).orElse(null);
    if (missing == null) {
      return "You're wearing illegal equipment because you're missing a slot token.";
    }
    int mainHandId = state.getEquippedItemId(SlotType.MAIN_HAND);
    int offHandId = state.getEquippedItemId(SlotType.OFF_HAND);
    if (missing == SlotType.OFF_HAND && mainHandId > 0 && mainHandId == offHandId) {
      return "Your two-handed weapon needs both the "
          + displayService.getDisplayName(SlotType.MAIN_HAND).toLowerCase()
          + " and "
          + displayService.getDisplayName(SlotType.OFF_HAND).toLowerCase()
          + " slot tokens. You're missing the "
          + displayService.getDisplayName(SlotType.OFF_HAND).toLowerCase()
          + " token.";
    }
    return "You cannot equip anything in the "
        + displayService.getDisplayName(missing).toLowerCase()
        + " slot without the slot token.";
  }

  private void queueMessage(String message) {
    chatMessageManager.queue(
        QueuedMessage.builder()
            .type(ChatMessageType.GAMEMESSAGE)
            .runeLiteFormattedMessage(ColorUtil.wrapWithColorTag(message, Color.RED))
            .build());
  }
}
