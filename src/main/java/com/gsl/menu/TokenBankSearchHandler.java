package com.gsl.menu;

import com.gsl.model.SlotType;
import com.gsl.service.SlotDisplayService;
import com.gsl.util.BankItemUtils;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;

/**
 * Extends RuneLite bank search so slot tokens and their placeholders match custom slot names
 * (e.g. searching {@code ammo} finds the Ammo slot token instead of only matching cape names).
 */
public class TokenBankSearchHandler {
  private static final String NUMBER_REGEX = "[0-9]+(\\.[0-9]+)?[kmb]?";
  private static final Pattern VALUE_SEARCH_PATTERN =
      Pattern.compile(
          "^(?<mode>qty|ge|ha|alch)?"
              + " *(?<individual>i|iv|individual|per)?"
              + " *(((?<op>[<>=]|>=|<=) *(?<num>"
              + NUMBER_REGEX
              + "))|"
              + "((?<num1>"
              + NUMBER_REGEX
              + ") *- *(?<num2>"
              + NUMBER_REGEX
              + ")))$",
          Pattern.CASE_INSENSITIVE);

  private final Client client;
  private final SlotDisplayService displayService;
  private final ItemManager itemManager;

  @Inject
  TokenBankSearchHandler(Client client, SlotDisplayService displayService, ItemManager itemManager) {
    this.client = client;
    this.displayService = displayService;
    this.itemManager = itemManager;
  }

  @Subscribe(priority = 0)
  public void onScriptCallbackEvent(ScriptCallbackEvent event) {
    if (!"bankSearchFilter".equals(event.getEventName())) {
      return;
    }

    int[] intStack = client.getIntStack();
    Object[] objectStack = client.getObjectStack();
    int intStackSize = client.getIntStackSize();
    int objectStackSize = client.getObjectStackSize();

    int itemId = intStack[intStackSize - 1];
    if (itemId <= 0) {
      return;
    }

    String search = (String) objectStack[objectStackSize - 1];
    if (search == null || search.isEmpty() || isDelegatedBankSearch(search)) {
      return;
    }

    SlotType slot = BankItemUtils.resolveSlotType(itemManager, itemId);
    if (slot == null) {
      return;
    }

    if (displayService.matchesBankSearch(slot, search)) {
      intStack[intStackSize - 2] = 1;
    }
  }

  private static boolean isDelegatedBankSearch(String search) {
    String trimmed = search.trim();
    if (trimmed.regionMatches(true, 0, "tag:", 0, 4)) {
      return true;
    }
    return VALUE_SEARCH_PATTERN.matcher(trimmed).matches();
  }
}
