package com.gsl;

import com.google.inject.Provides;
import com.gsl.menu.SlotMenuHandler;
import com.gsl.menu.TokenBankSearchHandler;
import com.gsl.overlay.EquipmentTokenClaimOverlay;
import com.gsl.overlay.ItemRestrictionOverlay;
import com.gsl.overlay.TokenBankDragOverlay;
import com.gsl.overlay.TokenInventoryDragOverlay;
import com.gsl.overlay.TokenPressHoldOverlay;
import com.gsl.overlay.TokenItemDragOverlay;
import com.gsl.overlay.TokenTooltipOverlay;
import com.gsl.overlay.ViolationOverlay;
import com.gsl.service.SlotDisplayService;
import com.gsl.service.SlotStateService;
import com.gsl.service.TokenModelOverrideService;
import com.gsl.service.ViolationNotifier;
import com.gsl.ui.GroupSlotLockedPanel;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.ConfigSync;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
    name = "Group Slot Locked",
    description = "Slot token claims and equip limits for group ironman teams",
    tags = {"ironman", "group", "equipment"},
    enabledByDefault = false)
public class GroupSlotLockedPlugin extends Plugin {
  @Inject private Client client;
  @Inject private SlotStateService slotStateService;
  @Inject private SlotDisplayService displayService;
  @Inject private TokenModelOverrideService tokenModelOverrideService;
  @Inject private ViolationNotifier violationNotifier;
  @Inject private SlotMenuHandler slotMenuHandler;
  @Inject private TokenBankSearchHandler tokenBankSearchHandler;
  @Inject private ItemRestrictionOverlay itemRestrictionOverlay;
  @Inject private TokenInventoryDragOverlay tokenInventoryDragOverlay;
  @Inject private TokenBankDragOverlay tokenBankDragOverlay;
  @Inject private TokenPressHoldOverlay tokenPressHoldOverlay;
  @Inject private TokenItemDragOverlay tokenItemDragOverlay;
  @Inject private TokenTooltipOverlay tokenTooltipOverlay;
  @Inject private ViolationOverlay violationOverlay;
  @Inject private EquipmentTokenClaimOverlay equipmentTokenClaimOverlay;
  @Inject private GroupSlotLockedPanel panel;
  @Inject private OverlayManager overlayManager;
  @Inject private ClientToolbar clientToolbar;
  @Inject private EventBus eventBus;
  @Inject private ClientThread clientThread;
  @Inject private GroupSlotLockedConfig config;
  private NavigationButton navButton;

  @Override
  protected void startUp() {
    displayService.ensureDefaultIcons();
    displayService.warmIconCache();
    BufferedImage icon;
    try {
      icon = ImageUtil.loadImageResource(getClass(), "/panel_icon.png");
    } catch (RuntimeException e) {
      log.warn("Failed to load panel icon, using blank fallback", e);
      icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }
    navButton =
        NavigationButton.builder()
            .tooltip("Group Slot Locked")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();
    if (config.showSlotPanel()) {
      clientToolbar.addNavigation(navButton);
    }
    overlayManager.add(itemRestrictionOverlay);
    overlayManager.add(tokenInventoryDragOverlay);
    overlayManager.add(tokenBankDragOverlay);
    overlayManager.add(tokenPressHoldOverlay);
    overlayManager.add(tokenItemDragOverlay);
    overlayManager.add(tokenTooltipOverlay);
    overlayManager.add(violationOverlay);
    overlayManager.add(equipmentTokenClaimOverlay);
    eventBus.register(slotMenuHandler);
    eventBus.register(tokenBankSearchHandler);
    eventBus.register(tokenModelOverrideService);
    slotStateService.addListener(violationNotifier::onStateChanged);
    clientThread.invoke(
        () -> {
          slotStateService.refreshAll();
          violationNotifier.onStateChanged(slotStateService.getState());
          if (config.replaceTokenIcons()) {
            tokenModelOverrideService.warmUp();
          }
        });
    log.info("Group Slot Locked started");
  }

  @Override
  protected void shutDown() {
    clientToolbar.removeNavigation(navButton);
    overlayManager.remove(itemRestrictionOverlay);
    overlayManager.remove(tokenInventoryDragOverlay);
    overlayManager.remove(tokenBankDragOverlay);
    overlayManager.remove(tokenPressHoldOverlay);
    overlayManager.remove(tokenItemDragOverlay);
    overlayManager.remove(tokenTooltipOverlay);
    overlayManager.remove(violationOverlay);
    overlayManager.remove(equipmentTokenClaimOverlay);
    eventBus.unregister(slotMenuHandler);
    eventBus.unregister(tokenBankSearchHandler);
    eventBus.unregister(tokenModelOverrideService);
    slotStateService.removeListener(violationNotifier::onStateChanged);
    violationNotifier.reset();
    clientThread.invoke(tokenModelOverrideService::restore);
    log.debug("Group Slot Locked stopped");
  }

  @Subscribe
  public void onGameTick(GameTick tick) {
    slotStateService.refreshInventoryAndWorn();
    int interval = Math.max(1, config.bankRefreshInterval());
    if (client.getTickCount() % interval == 0) {
      slotStateService.refreshBankIfAvailable();
    }
    slotStateService.refreshGroupStorage();
  }

  @Subscribe
  public void onItemContainerChanged(ItemContainerChanged event) {
    int id = event.getContainerId();
    if (id == InventoryID.INV
        || id == InventoryID.BANK
        || id == InventoryID.WORN
        || id == InventoryID.INV_GROUP_TEMP) {
      if (id == InventoryID.INV_GROUP_TEMP) {
        slotStateService.refreshGroupStorage();
      } else {
        slotStateService.refreshAll();
      }
    }
  }

  @Subscribe
  public void onScriptPostFired(ScriptPostFired event) {
    if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING
        || event.getScriptId() == ScriptID.GROUP_IRONMAN_STORAGE_BUILD) {
      slotStateService.refreshAll();
    }
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged event) {
    if (!"group-slot-locked".equals(event.getGroup()) || !"showTokenBadge".equals(event.getKey())) {
      return;
    }
    clientThread.invoke(
        () -> {
          if (config.replaceTokenIcons()) {
            tokenModelOverrideService.warmUp();
          } else {
            tokenModelOverrideService.restore();
          }
        });
  }

  @Subscribe
  public void onConfigSync(ConfigSync event) {
    if (client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    slotStateService.reloadSnapshots();
    slotStateService.refreshAll();
    violationNotifier.onStateChanged(slotStateService.getState());
  }

  @Subscribe
  public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
    slotStateService.reloadSnapshots();
    slotStateService.refreshAll();
    violationNotifier.onStateChanged(slotStateService.getState());
  }

  @Subscribe
  public void onGameStateChanged(GameStateChanged event) {
    if (event.getGameState() == GameState.LOGIN_SCREEN
        || event.getGameState() == GameState.CONNECTION_LOST) {
      slotStateService.onLogout();
      return;
    }
    if (event.getGameState() == GameState.LOGGED_IN) {
      clientThread.invokeLater(
          () -> {
            slotStateService.onLogin();
            slotStateService.refreshAll();
            violationNotifier.reset();
            violationNotifier.onStateChanged(slotStateService.getState());
          });
    }
  }

  @Provides
  GroupSlotLockedConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(GroupSlotLockedConfig.class);
  }
}
