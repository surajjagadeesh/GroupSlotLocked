package com.gsl.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

@Slf4j
@Singleton
public class SlotStateService {
  private static final String CONFIG_GROUP = "group-slot-locked";
  private static final String KEY_BANK_TOKEN_SLOTS = "snapshotBankSlots";
  private static final String KEY_INVENTORY_TOKEN_SLOTS = "snapshotInventorySlots";

  private static final Path DATA_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("group-slot-locked");
  private static final Path LOCAL_SNAPSHOT_FILE = DATA_DIR.resolve("held-tokens.properties");
  private static final Path SNAPSHOT_FILE = DATA_DIR.resolve("held-tokens.json");
  private static final Path LEGACY_BANK_SNAPSHOT_FILE = DATA_DIR.resolve("bank-tokens.json");

  private final Client client;
  private final ItemManager itemManager;
  private final ConfigManager configManager;
  private final Gson gson;
  @Getter private volatile LocalSlotState state = LocalSlotState.empty();
  private EnumSet<SlotType> lastKnownBankTokens = EnumSet.noneOf(SlotType.class);
  private int lastKnownBankTokenCount = 0;
  private EnumSet<SlotType> lastKnownInventoryTokens = EnumSet.noneOf(SlotType.class);
  private int lastKnownInventoryTokenCount = 0;
  private final List<Consumer<LocalSlotState>> listeners = new ArrayList<>();

  @Inject
  SlotStateService(
      Client client, ItemManager itemManager, ConfigManager configManager, Gson gson) {
    this.client = client;
    this.itemManager = itemManager;
    this.configManager = configManager;
    this.gson = gson;
    loadLocalSnapshotFile();
  }

  public void addListener(Consumer<LocalSlotState> listener) {
    listeners.add(listener);
  }

  public void removeListener(Consumer<LocalSlotState> listener) {
    listeners.remove(listener);
  }

  public void onLogin() {
    reloadSnapshots();
  }

  public void onLogout() {
    persistSnapshots();
  }

  /** Reload token slot snapshots from local file, then RS profile config when available. */
  public void reloadSnapshots() {
    loadLocalSnapshotFile();
    loadFromRsProfile();
    if (lastKnownBankTokens.isEmpty() && lastKnownInventoryTokens.isEmpty()) {
      migrateLegacySnapshotFiles();
    }
  }

  public void refreshAll() {
    refreshInventoryAndWorn();
    refreshBankIfAvailable();
    refreshGroupStorage();
  }

  public void refreshInventoryAndWorn() {
    EnumSet<SlotType> tokensPresent = EnumSet.noneOf(SlotType.class);
    int heldTokenCount = 0;
    TokenTotals inventoryTotals =
        resolveInventoryTokens(client.getItemContainer(InventoryID.INV));
    heldTokenCount += inventoryTotals.count;
    tokensPresent.addAll(inventoryTotals.slots);
    TokenTotals bankTotals = resolveBankTokens(client.getItemContainer(InventoryID.BANK));
    heldTokenCount += bankTotals.count;
    tokensPresent.addAll(bankTotals.slots);
    EnumSet<SlotType> equippedSlots = EnumSet.noneOf(SlotType.class);
    EnumMap<SlotType, Integer> equippedItemIds = new EnumMap<>(SlotType.class);
    ItemContainer worn = client.getItemContainer(InventoryID.WORN);
    if (worn != null) {
      for (SlotType slot : SlotType.values()) {
        Item item = worn.getItem(slot.getEquipmentSlot().getSlotIdx());
        if (item != null && item.getId() > 0) {
          equippedSlots.add(slot);
          equippedItemIds.put(slot, itemManager.canonicalize(item.getId()));
        }
      }
    }
    updateState(
        LocalSlotState.of(
            tokensPresent,
            state.getTokensInGroupStorage(),
            equippedSlots,
            equippedItemIds,
            heldTokenCount));
  }

  public void refreshBankIfAvailable() {
    ItemContainer bank = client.getItemContainer(InventoryID.BANK);
    if (bank == null) {
      return;
    }
    EnumSet<SlotType> tokensPresent = EnumSet.noneOf(SlotType.class);
    int heldTokenCount = 0;
    TokenTotals inventoryTotals =
        resolveInventoryTokens(client.getItemContainer(InventoryID.INV));
    heldTokenCount += inventoryTotals.count;
    tokensPresent.addAll(inventoryTotals.slots);
    TokenTotals bankTotals = resolveBankTokens(bank);
    heldTokenCount += bankTotals.count;
    tokensPresent.addAll(bankTotals.slots);
    updateState(
        LocalSlotState.of(
            tokensPresent,
            state.getTokensInGroupStorage(),
            state.getEquippedSlots(),
            state.getEquippedItemIds(),
            heldTokenCount));
  }

  public void refreshGroupStorage() {
    EnumSet<SlotType> groupStorageTokens = EnumSet.noneOf(SlotType.class);
    collectTokenTypes(client.getItemContainer(InventoryID.INV_GROUP_TEMP), groupStorageTokens);
    if (groupStorageTokens.equals(state.getTokensInGroupStorage())) {
      return;
    }
    updateState(
        LocalSlotState.of(
            state.getTokensPresent(),
            groupStorageTokens,
            state.getEquippedSlots(),
            state.getEquippedItemIds(),
            state.getHeldTokenCount()));
  }

  private TokenTotals resolveInventoryTokens(ItemContainer inventory) {
    if (inventory == null) {
      return TokenTotals.from(lastKnownInventoryTokens, lastKnownInventoryTokenCount);
    }
    EnumSet<SlotType> scanned = EnumSet.noneOf(SlotType.class);
    int scannedCount = collectTokens(inventory, scanned);
    if (shouldUpdateInventorySnapshot(scanned)) {
      captureInventorySnapshot(scanned, scannedCount);
      return TokenTotals.from(scanned, scannedCount);
    }
    return TokenTotals.from(lastKnownInventoryTokens, lastKnownInventoryTokenCount);
  }

  private TokenTotals resolveBankTokens(ItemContainer bank) {
    if (bank == null) {
      return TokenTotals.from(lastKnownBankTokens, lastKnownBankTokenCount);
    }
    EnumSet<SlotType> scanned = EnumSet.noneOf(SlotType.class);
    int scannedCount = collectTokens(bank, scanned);
    if (shouldUpdateBankSnapshot(scanned)) {
      captureBankSnapshot(scanned, scannedCount);
      return TokenTotals.from(scanned, scannedCount);
    }
    return TokenTotals.from(lastKnownBankTokens, lastKnownBankTokenCount);
  }

  private boolean shouldUpdateInventorySnapshot(EnumSet<SlotType> scannedTokens) {
    return !scannedTokens.isEmpty() || isInventorySnapshotReliable();
  }

  private boolean shouldUpdateBankSnapshot(EnumSet<SlotType> scannedTokens) {
    return !scannedTokens.isEmpty() || isBankInterfaceOpen();
  }

  private boolean isInventorySnapshotReliable() {
    return client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null;
  }

  private void captureInventorySnapshot(EnumSet<SlotType> inventoryTokens, int inventoryCount) {
    lastKnownInventoryTokens = EnumSet.copyOf(inventoryTokens);
    lastKnownInventoryTokenCount = inventoryCount;
    persistSnapshots();
  }

  private void captureBankSnapshot(EnumSet<SlotType> bankTokens, int bankCount) {
    lastKnownBankTokens = EnumSet.copyOf(bankTokens);
    lastKnownBankTokenCount = bankCount;
    persistSnapshots();
  }

  private void persistSnapshots() {
    saveBankSlotsToProfile();
    saveInventorySlotsToProfile();
    saveLocalSnapshotFile();
  }

  private boolean isBankInterfaceOpen() {
    Widget bankItems = client.getWidget(InterfaceID.Bankmain.ITEMS);
    return bankItems != null && !bankItems.isHidden();
  }

  private void loadFromRsProfile() {
    String bankRaw = configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_BANK_TOKEN_SLOTS);
    String inventoryRaw =
        configManager.getRSProfileConfiguration(CONFIG_GROUP, KEY_INVENTORY_TOKEN_SLOTS);
    if (bankRaw != null) {
      applyBankTokens(deserializeSlots(bankRaw));
    }
    if (inventoryRaw != null) {
      applyInventoryTokens(deserializeSlots(inventoryRaw));
    }
  }

  private void applyBankTokens(EnumSet<SlotType> slots) {
    lastKnownBankTokens = EnumSet.copyOf(slots);
    lastKnownBankTokenCount = estimateTokenCount(slots);
  }

  private void applyInventoryTokens(EnumSet<SlotType> slots) {
    lastKnownInventoryTokens = EnumSet.copyOf(slots);
    lastKnownInventoryTokenCount = estimateTokenCount(slots);
  }

  private static int estimateTokenCount(EnumSet<SlotType> slots) {
    return slots.size();
  }

  private void saveBankSlotsToProfile() {
    persistSlotsToProfile(KEY_BANK_TOKEN_SLOTS, lastKnownBankTokens);
  }

  private void saveInventorySlotsToProfile() {
    persistSlotsToProfile(KEY_INVENTORY_TOKEN_SLOTS, lastKnownInventoryTokens);
  }

  private void persistSlotsToProfile(String key, EnumSet<SlotType> slots) {
    String serialized = serializeSlots(slots);
    if (serialized.isEmpty()) {
      configManager.unsetRSProfileConfiguration(CONFIG_GROUP, key);
    } else {
      configManager.setRSProfileConfiguration(CONFIG_GROUP, key, serialized);
    }
  }

  private void saveLocalSnapshotFile() {
    try {
      Files.createDirectories(DATA_DIR);
      Properties properties = new Properties();
      properties.setProperty("bank", serializeSlots(lastKnownBankTokens));
      properties.setProperty("inventory", serializeSlots(lastKnownInventoryTokens));
      try (Writer writer = Files.newBufferedWriter(LOCAL_SNAPSHOT_FILE)) {
        properties.store(writer, "Group Slot Locked held token slots");
      }
    } catch (IOException ex) {
      log.debug("Failed to save local token snapshot to {}", LOCAL_SNAPSHOT_FILE, ex);
    }
  }

  private void loadLocalSnapshotFile() {
    if (!Files.isRegularFile(LOCAL_SNAPSHOT_FILE)) {
      return;
    }
    try (Reader reader = Files.newBufferedReader(LOCAL_SNAPSHOT_FILE)) {
      Properties properties = new Properties();
      properties.load(reader);
      applyBankTokens(deserializeSlots(properties.getProperty("bank", "")));
      applyInventoryTokens(deserializeSlots(properties.getProperty("inventory", "")));
    } catch (IOException | RuntimeException ex) {
      log.debug("Failed to load local token snapshot from {}", LOCAL_SNAPSHOT_FILE, ex);
    }
  }

  private static String serializeSlots(EnumSet<SlotType> slots) {
    if (slots.isEmpty()) {
      return "";
    }
    return slots.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
  }

  private static EnumSet<SlotType> deserializeSlots(String serialized) {
    EnumSet<SlotType> slots = EnumSet.noneOf(SlotType.class);
    if (serialized == null || serialized.trim().isEmpty()) {
      return slots;
    }
    for (String part : serialized.split(",")) {
      String slotName = part.trim();
      if (slotName.isEmpty()) {
        continue;
      }
      try {
        slots.add(SlotType.valueOf(slotName.toUpperCase(Locale.ENGLISH)));
      } catch (IllegalArgumentException ex) {
        log.debug("Ignoring unknown slot in token snapshot: {}", slotName);
      }
    }
    return slots;
  }

  private void migrateLegacySnapshotFiles() {
    LegacySnapshot legacy = readLegacySnapshotFile();
    if (legacy == null) {
      return;
    }
    if (!legacy.bankSlots.isEmpty()) {
      applyBankTokens(legacy.bankSlots);
    }
    if (!legacy.inventorySlots.isEmpty()) {
      applyInventoryTokens(legacy.inventorySlots);
    }
    persistSnapshots();
    deleteLegacySnapshotFiles();
  }

  private LegacySnapshot readLegacySnapshotFile() {
    if (Files.isRegularFile(SNAPSHOT_FILE)) {
      try (Reader reader = Files.newBufferedReader(SNAPSHOT_FILE)) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> raw = gson.fromJson(reader, type);
        if (raw != null) {
          return legacyFromRaw(raw);
        }
      } catch (IOException | RuntimeException ex) {
        log.debug("Failed to load legacy snapshot from {}", SNAPSHOT_FILE, ex);
      }
    }
    if (Files.isRegularFile(LEGACY_BANK_SNAPSHOT_FILE)) {
      try (Reader reader = Files.newBufferedReader(LEGACY_BANK_SNAPSHOT_FILE)) {
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> raw = gson.fromJson(reader, type);
        if (raw != null) {
          LegacySnapshot legacy = new LegacySnapshot();
          legacy.bankSlots = slotNamesFromRaw(raw.get("slots"));
          return legacy;
        }
      } catch (IOException | RuntimeException ex) {
        log.debug("Failed to load legacy bank snapshot from {}", LEGACY_BANK_SNAPSHOT_FILE, ex);
      }
    }
    return null;
  }

  private LegacySnapshot legacyFromRaw(Map<String, Object> raw) {
    LegacySnapshot legacy = new LegacySnapshot();
    legacy.bankSlots = slotNamesFromRaw(raw.get("bankSlots"));
    legacy.inventorySlots = slotNamesFromRaw(raw.get("inventorySlots"));
    return legacy;
  }

  @SuppressWarnings("unchecked")
  private EnumSet<SlotType> slotNamesFromRaw(Object rawSlots) {
    EnumSet<SlotType> slots = EnumSet.noneOf(SlotType.class);
    if (!(rawSlots instanceof List)) {
      return slots;
    }
    for (Object entry : (List<?>) rawSlots) {
      if (entry instanceof String) {
        try {
          slots.add(SlotType.valueOf(((String) entry).toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException ex) {
          log.debug("Ignoring unknown slot in legacy snapshot: {}", entry);
        }
      }
    }
    return slots;
  }

  private void deleteLegacySnapshotFiles() {
    try {
      Files.deleteIfExists(SNAPSHOT_FILE);
      Files.deleteIfExists(LEGACY_BANK_SNAPSHOT_FILE);
    } catch (IOException ex) {
      log.debug("Failed to delete legacy snapshot files", ex);
    }
  }

  private int collectTokens(ItemContainer container, EnumSet<SlotType> tokensPresent) {
    return collectTokenTypes(container, tokensPresent);
  }

  private int collectTokenTypes(ItemContainer container, EnumSet<SlotType> tokensPresent) {
    if (container == null) {
      return 0;
    }
    boolean bankContainer = container.getId() == InventoryID.BANK;
    int count = 0;
    for (Item item : container.getItems()) {
      if (item == null || item.getId() <= 0) {
        continue;
      }
      if (bankContainer && isBankPlaceholder(item)) {
        continue;
      }
      int canonicalId = itemManager.canonicalize(item.getId());
      SlotType slot = SlotType.fromTokenItemId(canonicalId);
      if (slot != null) {
        tokensPresent.add(slot);
        count += item.getQuantity();
      }
    }
    return count;
  }

  private boolean isBankPlaceholder(Item item) {
    if (item.getQuantity() <= 0) {
      return true;
    }
    return itemManager.getItemComposition(item.getId()).getPlaceholderTemplateId() != -1;
  }

  private void updateState(LocalSlotState newState) {
    if (state.getTokensPresent().equals(newState.getTokensPresent())
        && state.getTokensInGroupStorage().equals(newState.getTokensInGroupStorage())
        && state.getEquippedSlots().equals(newState.getEquippedSlots())
        && state.getEquippedItemIds().equals(newState.getEquippedItemIds())
        && state.getHeldTokenCount() == newState.getHeldTokenCount()) {
      return;
    }
    state = newState;
    for (Consumer<LocalSlotState> listener : listeners) {
      listener.accept(state);
    }
  }

  private static final class TokenTotals {
    private final EnumSet<SlotType> slots;
    private final int count;

    private TokenTotals(EnumSet<SlotType> slots, int count) {
      this.slots = slots;
      this.count = count;
    }

    private static TokenTotals from(EnumSet<SlotType> slots, int count) {
      return new TokenTotals(EnumSet.copyOf(slots), count);
    }
  }

  private static final class LegacySnapshot {
    private EnumSet<SlotType> bankSlots = EnumSet.noneOf(SlotType.class);
    private EnumSet<SlotType> inventorySlots = EnumSet.noneOf(SlotType.class);
  }
}
