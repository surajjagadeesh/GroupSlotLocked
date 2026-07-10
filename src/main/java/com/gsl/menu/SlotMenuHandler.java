package com.gsl.menu;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.GroupSlotLockedConfig.TokenLeftClick;
import com.gsl.model.SlotType;
import com.gsl.model.Violation;
import com.gsl.service.SlotDisplayService;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import com.gsl.util.BankItemUtils;
import com.gsl.util.TokenItemWidgetScopes;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ChatLineBuffer;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

public class SlotMenuHandler {
  private static final Set<String> EQUIP_OPTIONS = Set.of("wear", "wield", "equip");
  private int pendingSuppressTokenExamineItemId = -1;
  private int pendingSuppressTokenExamineUntilTick = -1;
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;
  private final SlotStateService slotStateService;
  private final SlotValidator slotValidator;
  private final ChatMessageManager chatMessageManager;
  private final ItemManager itemManager;

  @Inject
  SlotMenuHandler(
      Client client,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService,
      SlotStateService slotStateService,
      SlotValidator slotValidator,
      ChatMessageManager chatMessageManager,
      ItemManager itemManager) {
    this.client = client;
    this.config = config;
    this.displayService = displayService;
    this.slotStateService = slotStateService;
    this.slotValidator = slotValidator;
    this.chatMessageManager = chatMessageManager;
    this.itemManager = itemManager;
  }

  @Subscribe
  public void onMenuEntryAdded(MenuEntryAdded event) {
    if (!config.enablePlugin()) {
      return;
    }
    MenuEntry entry = event.getMenuEntry();
    if (!TokenItemWidgetScopes.isTokenItemMenuContext(entry)) {
      return;
    }
    int itemId = resolveItemId(entry, event.getItemId());
    if (itemId <= 0) {
      return;
    }
    if (resolveTokenSlot(itemId) != null) {
      handleTokenEntry(entry, itemId);
      return;
    }
    if (config.deprioritizeIllegalEquips()
        && isEquipOption(entry.getOption())
        && slotValidator.getViolationForItem(slotStateService.getState(), itemId)
            != Violation.NONE) {
      entry.setDeprioritized(true);
    }
  }

  @Subscribe
  public void onMenuOpened(MenuOpened event) {
    if (!config.enablePlugin()) {
      return;
    }
    MenuEntry[] entries = event.getMenuEntries();
    boolean changed = false;
    for (int i = 0; i < entries.length; i++) {
      MenuEntry entry = entries[i];
      int itemId = resolveItemId(entry, entry.getItemId());
      if (itemId <= 0) {
        continue;
      }
      SlotType slot = resolveTokenSlot(itemId);
      if (slot != null) {
        if (handleTokenMenuOpenedEntry(entry, entries, i, slot)) {
          changed = true;
        }
        continue;
      }
      if (config.deprioritizeIllegalEquips()
          && isEquipOption(entry.getOption())
          && slotValidator.getViolationForItem(slotStateService.getState(), itemId)
              != Violation.NONE) {
        entry.setDeprioritized(true);
        changed = true;
      }
    }
    if (changed) {
      event.setMenuEntries(entries);
    }
  }

  @Subscribe(priority = 1)
  public void onPostMenuSort(PostMenuSort event) {
    if (!config.enablePlugin() || client.isMenuOpen()) {
      return;
    }
    applyTokenMenuChanges(client.getMenuEntries());
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked event) {
    if (!config.enablePlugin()) {
      return;
    }
    MenuEntry entry = event.getMenuEntry();
    int itemId = resolveItemId(entry, entry.getItemId());
    if (itemId <= 0) {
      return;
    }
    SlotType slot = resolveTokenSlot(itemId);
    if (slot != null && isExamineEntry(entry)) {
      pendingSuppressTokenExamineItemId = itemId;
      pendingSuppressTokenExamineUntilTick = client.getTickCount() + 5;
      if (config.tokenExamineHint()) {
        chatMessageManager.queue(
            QueuedMessage.builder()
                .type(ChatMessageType.GAMEMESSAGE)
                .runeLiteFormattedMessage(displayService.getTokenExamineChatMessage(slot))
                .build());
      }
      return;
    }
    if (!config.blockIllegalEquips() || !isEquipOption(event.getMenuOption())) {
      return;
    }
    if (resolveTokenSlot(itemId) != null
        || slotValidator.getViolationForItem(slotStateService.getState(), itemId)
            != Violation.NONE) {
      event.consume();
    }
  }

  @Subscribe
  public void onClientTick(ClientTick event) {
    if (pendingSuppressTokenExamineItemId > 0
        && client.getTickCount() > pendingSuppressTokenExamineUntilTick) {
      clearPendingTokenExamineSuppression();
    }
  }

  @Subscribe(priority = Short.MAX_VALUE)
  public void onScriptCallbackEvent(ScriptCallbackEvent event) {
    if (!config.enablePlugin() || !isSuppressingTokenExamine()) {
      return;
    }
    if (!"chatFilterCheck".equals(event.getEventName())) {
      return;
    }

    int[] intStack = client.getIntStack();
    int intStackSize = client.getIntStackSize();
    if (intStack[intStackSize - 2] != ChatMessageType.ITEM_EXAMINE.getType()) {
      return;
    }

    intStack[intStackSize - 3] = 0;
  }

  @Subscribe(priority = -2)
  public void onChatMessage(ChatMessage event) {
    if (!config.enablePlugin() || !isSuppressingTokenExamine()) {
      return;
    }
    if (event.getType() != ChatMessageType.ITEM_EXAMINE) {
      return;
    }

    MessageNode messageNode = event.getMessageNode();
    ChatLineBuffer chatLineBuffer =
        client.getChatLineMap().get(ChatMessageType.ITEM_EXAMINE.getType());
    if (chatLineBuffer != null) {
      chatLineBuffer.removeMessageNode(messageNode);
    }
  }

  private boolean isSuppressingTokenExamine() {
    return pendingSuppressTokenExamineItemId > 0
        && client.getTickCount() <= pendingSuppressTokenExamineUntilTick;
  }

  private void clearPendingTokenExamineSuppression() {
    pendingSuppressTokenExamineItemId = -1;
    pendingSuppressTokenExamineUntilTick = -1;
  }

  private void handleTokenEntry(MenuEntry entry, int itemId) {
    SlotType slot = resolveTokenSlot(itemId);
    if (slot == null) {
      return;
    }
    applyTokenHoverTarget(entry, slot);
    if (!TokenItemWidgetScopes.isTokenLeftClickReorderContext(entry)) {
      return;
    }
    if (shouldDeprioritizeTokenOption(entry.getOption())) {
      entry.setDeprioritized(true);
    }
    if (isPreferredTokenOption(entry, config.tokenLeftClick())) {
      if (config.tokenLeftClick() == TokenLeftClick.EXAMINE && isExamineEntry(entry)) {
        applyTokenSlotMenuText(entry, slot);
      }
    }
  }

  private boolean handleTokenMenuOpenedEntry(
      MenuEntry entry, MenuEntry[] entries, int index, SlotType slot) {
    boolean changed = applyTokenHoverTarget(entry, slot);
    if (!TokenItemWidgetScopes.isTokenLeftClickReorderContext(entry)) {
      return changed;
    }
    if (shouldDeprioritizeTokenOption(entry.getOption())) {
      entry.setDeprioritized(true);
      changed = true;
    }
    if (isPreferredTokenOption(entry, config.tokenLeftClick())) {
      if (config.tokenLeftClick() == TokenLeftClick.EXAMINE && isExamineEntry(entry)) {
        applyTokenSlotMenuText(entry, slot);
      }
      promoteEntry(entries, index);
      changed = true;
    }
    return changed;
  }

  private void applyTokenMenuChanges(MenuEntry[] entries) {
    int preferredIndex = -1;
    SlotType slot = null;
    boolean changed = false;
    boolean reorderContext = false;

    for (int i = 0; i < entries.length; i++) {
      MenuEntry entry = entries[i];
      if (!TokenItemWidgetScopes.isTokenItemMenuContext(entry)) {
        continue;
      }
      int itemId = resolveItemId(entry, entry.getItemId());
      SlotType entrySlot = resolveTokenSlot(itemId);
      if (entrySlot == null) {
        continue;
      }
      if (slot == null) {
        slot = entrySlot;
      }
      if (applyTokenHoverTarget(entry, entrySlot)) {
        changed = true;
      }

      if (!TokenItemWidgetScopes.isTokenLeftClickReorderContext(entry)) {
        continue;
      }
      reorderContext = true;
      if (shouldDeprioritizeTokenOption(entry.getOption())) {
        entry.setDeprioritized(true);
      }
      if (isPreferredTokenOption(entry, config.tokenLeftClick())) {
        preferredIndex = i;
      }
    }

    if (reorderContext && preferredIndex >= 0 && slot != null) {
      if (config.tokenLeftClick() == TokenLeftClick.EXAMINE && isExamineEntry(entries[preferredIndex])) {
        applyTokenSlotMenuText(entries[preferredIndex], slot);
      }
      if (preferredIndex < entries.length - 1) {
        promoteEntry(entries, preferredIndex);
      }
      if (config.tokenLeftClick() == TokenLeftClick.EXAMINE) {
        prepareExamineForLeftClick(entries[entries.length - 1]);
      }
      changed = true;
    }

    if (changed) {
      client.setMenuEntries(entries);
    }
  }

  private boolean shouldDeprioritizeTokenOption(String option) {
    if (option == null) {
      return false;
    }
    switch (config.tokenLeftClick()) {
      case EXAMINE:
        return isEquipOption(option)
            || option.equalsIgnoreCase("use")
            || option.equalsIgnoreCase("drop");
      case USE:
        return isEquipOption(option) || isExamineOption(option) || option.equalsIgnoreCase("drop");
      case DROP:
        return isEquipOption(option) || isExamineOption(option) || option.equalsIgnoreCase("use");
      default:
        return config.deprioritizeTokenWear() && isEquipOption(option);
    }
  }

  private void applyTokenSlotMenuText(MenuEntry entry, SlotType slot) {
    applyTokenExamineMenuText(entry, slot);
  }

  private void applyTokenExamineMenuText(MenuEntry entry, SlotType slot) {
    entry.setOption(getExamineVerb(entry));
    entry.setTarget(getColoredSlotTarget(entry, slot));
  }

  private String getColoredSlotTarget(MenuEntry entry, SlotType slot) {
    return ColorUtil.wrapWithColorTag(
        displayService.getHoverTargetText(slot, resolveEntryQuantity(entry)),
        JagexColors.MENU_TARGET);
  }

  private int resolveEntryQuantity(MenuEntry entry) {
    Widget widget = entry.getWidget();
    if (widget == null) {
      return 1;
    }
    return BankItemUtils.resolvePlaceholderDisplayQuantity(
        itemManager, widget.getItemId(), widget.getItemQuantity());
  }

  private boolean applyTokenHoverTarget(MenuEntry entry, SlotType slot) {
    if (TokenItemWidgetScopes.isTokenBankActionContext(entry)) {
      if (isExamineEntry(entry)) {
        applyTokenExamineMenuText(entry, slot);
        return true;
      }
      entry.setTarget(getColoredSlotTarget(entry, slot));
      return true;
    }

    if (isExamineEntry(entry)) {
      applyTokenExamineMenuText(entry, slot);
      return true;
    }

    String slotLabel = displayService.getHoverTargetText(slot, resolveEntryQuantity(entry));
    entry.setTarget(ColorUtil.wrapWithColorTag(slotLabel, JagexColors.MENU_TARGET));
    return true;
  }

  private SlotType resolveTokenSlot(int itemId) {
    return BankItemUtils.resolveSlotType(itemManager, itemId);
  }

  private static int resolveItemId(MenuEntry entry, int itemId) {
    if (itemId > 0) {
      return itemId;
    }
    Widget widget = entry.getWidget();
    return widget != null ? widget.getItemId() : -1;
  }

  private static void promoteEntry(MenuEntry[] entries, int index) {
    if (index < 0 || index >= entries.length - 1) {
      return;
    }
    MenuEntry promoted = entries[index];
    System.arraycopy(entries, index + 1, entries, index, entries.length - index - 1);
    entries[entries.length - 1] = promoted;
  }

  /**
   * Examine is added as {@link MenuAction#CC_OP_LOW_PRIORITY}, which is right-click only even when
   * promoted to the top of the menu. Upgrade it so left-click executes instead of opening the menu.
   */
  private static void prepareExamineForLeftClick(MenuEntry entry) {
    if (entry == null || !isExamineEntry(entry)) {
      return;
    }
    if (entry.getType() == MenuAction.CC_OP_LOW_PRIORITY) {
      entry.setType(MenuAction.CC_OP);
    }
    entry.setForceLeftClick(true);
  }

  private static boolean isPreferredTokenOption(MenuEntry entry, TokenLeftClick preferred) {
    if (entry == null) {
      return false;
    }
    String option = entry.getOption();
    switch (preferred) {
      case EXAMINE:
        return isExamineEntry(entry);
      case DROP:
        return option != null && option.equalsIgnoreCase("drop");
      case USE:
        return option != null && option.equalsIgnoreCase("use");
      default:
        return false;
    }
  }

  private static boolean isExamineEntry(MenuEntry entry) {
    if (entry == null) {
      return false;
    }
    if (entry.getIdentifier() == 10) {
      return true;
    }
    return isExamineOption(entry.getOption());
  }

  private static boolean isEquipOption(String option) {
    return option != null && EQUIP_OPTIONS.contains(option.toLowerCase(Locale.ENGLISH));
  }

  private static String getExamineVerb(MenuEntry entry) {
    String option = entry.getOption();
    if (option != null && option.toLowerCase(Locale.ENGLISH).startsWith("inspect")) {
      return "Inspect";
    }
    return "Examine";
  }

  private static boolean isExamineOption(String option) {
    if (option == null) {
      return false;
    }
    String lower = option.toLowerCase(Locale.ENGLISH);
    return lower.startsWith("examine") || lower.startsWith("inspect");
  }
}
