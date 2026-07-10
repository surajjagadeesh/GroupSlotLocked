package com.gsl.menu;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.GroupSlotLockedConfig.TokenLeftClick;
import com.gsl.model.SlotType;
import com.gsl.model.Violation;
import com.gsl.service.SlotDisplayService;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import com.gsl.util.TokenItemWidgetScopes;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.util.ColorUtil;

public class SlotMenuHandler {
  private static final Set<String> EQUIP_OPTIONS = Set.of("wear", "wield", "equip");
  private final Client client;
  private final GroupSlotLockedConfig config;
  private final SlotDisplayService displayService;
  private final SlotStateService slotStateService;
  private final SlotValidator slotValidator;
  private final ChatMessageManager chatMessageManager;

  @Inject
  SlotMenuHandler(
      Client client,
      GroupSlotLockedConfig config,
      SlotDisplayService displayService,
      SlotStateService slotStateService,
      SlotValidator slotValidator,
      ChatMessageManager chatMessageManager) {
    this.client = client;
    this.config = config;
    this.displayService = displayService;
    this.slotStateService = slotStateService;
    this.slotValidator = slotValidator;
    this.chatMessageManager = chatMessageManager;
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
    if (SlotType.isTokenItem(itemId)) {
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
      if (SlotType.isTokenItem(itemId)) {
        if (handleTokenMenuOpenedEntry(entry, entries, i, SlotType.fromTokenItemId(itemId))) {
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

  @Subscribe(priority = -1)
  public void onPostMenuSort(PostMenuSort event) {
    if (!config.enablePlugin() || client.isMenuOpen()) {
      return;
    }
    applyTokenLeftClickOrder(client.getMenuEntries());
  }

  @Subscribe
  public void onBeforeRender(BeforeRender event) {
    if (!config.enablePlugin()) {
      return;
    }
    for (MenuEntry entry : client.getMenuEntries()) {
      if (!TokenItemWidgetScopes.isTokenItemMenuContext(entry)) {
        continue;
      }
      int itemId = resolveItemId(entry, entry.getItemId());
      if (!SlotType.isTokenItem(itemId)) {
        continue;
      }
      SlotType slot = SlotType.fromTokenItemId(itemId);
      if (slot != null) {
        applyTokenHoverTarget(entry, slot);
      }
    }
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked event) {
    if (!config.enablePlugin()) {
      return;
    }
    int itemId = event.getMenuEntry().getItemId();
    if (itemId <= 0) {
      return;
    }
    if (SlotType.isTokenItem(itemId)
        && isExamineEntry(event.getMenuEntry())
        && config.tokenExamineHint()) {
      SlotType slot = SlotType.fromTokenItemId(itemId);
      chatMessageManager.queue(
          QueuedMessage.builder()
              .type(ChatMessageType.GAMEMESSAGE)
              .runeLiteFormattedMessage(
                  "Group Slot Locked: this token grants the <col=ff9040>"
                      + displayService.getDisplayName(slot)
                      + "</col> equipment slot.")
              .build());
    }
    if (!config.blockIllegalEquips()
        || !isEquipAction(event.getMenuAction(), event.getMenuOption())) {
      return;
    }
    if (SlotType.isTokenItem(itemId)
        || slotValidator.getViolationForItem(slotStateService.getState(), itemId)
            != Violation.NONE) {
      event.consume();
    }
  }

  private void handleTokenEntry(MenuEntry entry, int itemId) {
    SlotType slot = SlotType.fromTokenItemId(itemId);
    applyTokenHoverTarget(entry, slot);
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
    boolean changed = false;
    applyTokenHoverTarget(entry, slot);
    changed = true;
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

  private void applyTokenLeftClickOrder(MenuEntry[] entries) {
    int preferredIndex = -1;
    SlotType slot = null;
    boolean changed = false;

    for (int i = 0; i < entries.length; i++) {
      MenuEntry entry = entries[i];
      if (!TokenItemWidgetScopes.isTokenItemMenuContext(entry)) {
        continue;
      }
      int itemId = resolveItemId(entry, entry.getItemId());
      if (!SlotType.isTokenItem(itemId)) {
        continue;
      }
      if (slot == null) {
        slot = SlotType.fromTokenItemId(itemId);
      }
      applyTokenHoverTarget(entry, slot);
      if (shouldDeprioritizeTokenOption(entry.getOption())) {
        entry.setDeprioritized(true);
        changed = true;
      }
      if (isPreferredTokenOption(entry, config.tokenLeftClick())) {
        preferredIndex = i;
      }
    }

    if (preferredIndex >= 0 && slot != null) {
      if (config.tokenLeftClick() == TokenLeftClick.EXAMINE && isExamineEntry(entries[preferredIndex])) {
        applyTokenSlotMenuText(entries[preferredIndex], slot);
      }
      if (preferredIndex < entries.length - 1) {
        promoteEntry(entries, preferredIndex);
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
    entry.setOption(displayService.getExamineOptionText(slot));
    applyTokenHoverTarget(entry, slot);
  }

  private void applyTokenHoverTarget(MenuEntry entry, SlotType slot) {
    Widget widget = entry.getWidget();
    int quantity = widget != null ? widget.getItemQuantity() : 1;
    String text = displayService.getHoverTargetText(slot, quantity);
    entry.setTarget(ColorUtil.wrapWithColorTag(text, JagexColors.MENU_TARGET));
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

  private static boolean isExamineOption(String option) {
    return option != null && option.toLowerCase(Locale.ENGLISH).startsWith("examine");
  }

  private static boolean isEquipAction(MenuAction action, String option) {
    return isEquipOption(option)
        || action == MenuAction.WIDGET_TARGET
        || action == MenuAction.WIDGET_TARGET_ON_WIDGET
        || action == MenuAction.CC_OP;
  }
}
