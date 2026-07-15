package com.gsl.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gsl.model.SlotType;
import com.gsl.model.TokenIconRenderContext;
import com.gsl.model.TokenIconStyle;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.ui.FontManager;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
@Singleton
public class SlotDisplayService {
  private static final int MAX_ICON_DIMENSION = 64;
  private static final Path DATA_DIR = RuneLite.RUNELITE_DIR.toPath().resolve("group-slot-locked");
  private static final Path ICON_DIR = DATA_DIR.resolve("icons");
  private static final Path NAMES_FILE = DATA_DIR.resolve("slot-names.json");
  private final Gson gson;
  private final Map<SlotType, String> displayNames = new EnumMap<>(SlotType.class);
  private final Map<SlotType, BufferedImage> iconCache = new EnumMap<>(SlotType.class);
  private final Map<SlotType, BufferedImage> generatedDefaults = new EnumMap<>(SlotType.class);

  @Inject
  SlotDisplayService(Gson gson) {
    this.gson = gson;
    reloadNames();
  }

  public void reloadNames() {
    displayNames.clear();
    for (SlotType slot : SlotType.values()) {
      displayNames.put(slot, slot.getDefaultDisplayName());
    }
    if (Files.isRegularFile(NAMES_FILE)) {
      try (Reader reader = Files.newBufferedReader(NAMES_FILE)) {
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> fromFile = gson.fromJson(reader, type);
        if (fromFile != null) {
          applyNameOverrides(fromFile);
        }
      } catch (IOException | RuntimeException ex) {
        log.debug("Failed to load slot names from {}", NAMES_FILE, ex);
      }
    }
  }

  public void reloadIcons() {
    iconCache.clear();
  }

  /**
   * Copies bundled slot icons into {@code .runelite/group-slot-locked/icons/} when a slot has no
   * user override yet. Matches what right-click → Import icon does, without manual steps.
   */
  public void ensureDefaultIcons() {
    try {
      Files.createDirectories(ICON_DIR);
      boolean seeded = false;
      for (SlotType slot : SlotType.values()) {
        Path target = ICON_DIR.resolve(slot.fileName());
        if (Files.isRegularFile(target)) {
          continue;
        }
        BufferedImage bundled = loadBundledIcon(slot);
        if (bundled == null) {
          log.debug("No bundled icon available to seed for {}", slot);
          continue;
        }
        ImageIO.write(bundled, "png", target.toFile());
        seeded = true;
      }
      if (seeded) {
        reloadIcons();
      }
    } catch (IOException ex) {
      log.debug("Failed to seed default slot icons under {}", ICON_DIR, ex);
    }
  }

  public void warmIconCache() {
    for (SlotType slot : SlotType.values()) {
      getReplacementIcon(slot);
    }
  }

  public String getDisplayName(SlotType slot) {
    return displayNames.getOrDefault(slot, slot.getDefaultDisplayName());
  }

  public String getHoverTargetText(SlotType slot, int quantity) {
    String label = getDisplayName(slot) + " slot token";
    if (quantity > 1) {
      return QuantityFormatter.formatNumber(quantity) + " x " + label;
    }
    return label;
  }

  /**
   * Returns true when a bank search string should match this slot token, using the same substring
   * rules as vanilla item-name search ({@code label.contains(search)}).
   */
  public boolean matchesBankSearch(SlotType slot, String search) {
    if (slot == null || search == null) {
      return false;
    }
    String needle = search.trim().toLowerCase(Locale.ENGLISH);
    if (needle.isEmpty()) {
      return false;
    }
    for (String label : getBankSearchLabels(slot)) {
      if (label.toLowerCase(Locale.ENGLISH).contains(needle)) {
        return true;
      }
    }
    return false;
  }

  /** Searchable names for all 11 slot tokens in bank search. */
  public List<String> getBankSearchLabels(SlotType slot) {
    List<String> labels = new ArrayList<>();
    String displayName = getDisplayName(slot);
    labels.add(displayName);
    labels.add(displayName + " slot");
    labels.add(getHoverTargetText(slot, 1));
    labels.add(slot.name().toLowerCase(Locale.ENGLISH));
    labels.add(slot.name().toLowerCase(Locale.ENGLISH).replace('_', ' '));
    labels.add(slot.getDefaultDisplayName().toLowerCase(Locale.ENGLISH));
    labels.add(slot.getDefaultAbbrev().toLowerCase(Locale.ENGLISH));
    addBankSearchSynonyms(slot, labels);
    return labels;
  }

  private static void addBankSearchSynonyms(SlotType slot, List<String> labels) {
    switch (slot) {
      case NECK:
        labels.add("amulet");
        break;
      case MAIN_HAND:
        labels.add("weapon");
        labels.add("mainhand");
        break;
      case OFF_HAND:
        labels.add("shield");
        labels.add("offhand");
        break;
      case GLOVES:
        labels.add("hands");
        break;
      case BOOTS:
        labels.add("feet");
        break;
      default:
        break;
    }
  }

  public String getTokenExamineChatMessage(SlotType slot) {
    return "Allows for use of the " + getDisplayName(slot).toLowerCase() + " equipment slot.";
  }

  public BufferedImage getIcon(SlotType slot) {
    return iconCache.computeIfAbsent(slot, this::resolveIcon);
  }

  @Nullable
  public BufferedImage getReplacementIcon(SlotType slot) {
    BufferedImage icon = iconCache.get(slot);
    if (icon != null) {
      return icon;
    }
    icon = resolveIcon(slot);
    if (icon != null) {
      iconCache.put(slot, icon);
    }
    return icon;
  }

  public void drawReplacementIcon(Graphics2D graphics, SlotType slot, Rectangle bounds) {
    drawReplacementIcon(graphics, slot, bounds, TokenIconStyle.NORMAL);
  }

  public void drawReplacementIcon(
      Graphics2D graphics, SlotType slot, Rectangle bounds, TokenIconStyle style) {
    BufferedImage icon = getReplacementIcon(slot);
    if (icon == null) {
      return;
    }
    graphics.setColor(style.getBackground());
    graphics.fill(bounds);
    int padding = 1;
    int size = Math.min(bounds.width, bounds.height) - padding * 2;
    if (size <= 0) {
      return;
    }
    int x = bounds.x + (bounds.width - size) / 2;
    int y = bounds.y + (bounds.height - size) / 2;
    graphics.drawImage(composeOpaqueIcon(icon, size, style), x, y, null);
  }

  private static BufferedImage composeOpaqueIcon(
      BufferedImage icon, int size, TokenIconStyle style) {
    BufferedImage source = icon;
    float luminanceScale = style.getLuminanceScale();
    if (luminanceScale < 1.0f) {
      source = ImageUtil.luminanceScale(icon, luminanceScale);
    }
    BufferedImage composed = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D iconGraphics = composed.createGraphics();
    iconGraphics.setColor(style.getBackground());
    iconGraphics.fillRect(0, 0, size, size);
    iconGraphics.drawImage(source, 0, 0, size, size, null);
    iconGraphics.dispose();
    return composed;
  }

  public void drawItemQuantity(Graphics2D graphics, Rectangle bounds, int quantity) {
    if (quantity == 1) {
      return;
    }
    String text = QuantityFormatter.quantityToRSDecimalStack(quantity);
    Font font = FontManager.getRunescapeSmallFont();
    graphics.setFont(font);
    int x = bounds.x + 1;
    int y = bounds.y + bounds.height - 3;
    OverlayUtil.renderTextLocation(graphics, new Point(x, y), text, Color.YELLOW);
  }

  public void drawPlaceholderQuantity(Graphics2D graphics, Rectangle bounds, int quantity) {
    if (quantity == 1) {
      return;
    }
    String text = quantity <= 0 ? "0" : QuantityFormatter.quantityToRSDecimalStack(quantity);
    Font font = FontManager.getRunescapeSmallFont();
    graphics.setFont(font);
    FontMetrics metrics = graphics.getFontMetrics();
    int x = bounds.x + 1;
    int y = bounds.y + metrics.getAscent() + 1;
    OverlayUtil.renderTextLocation(graphics, new Point(x, y), text, new Color(170, 170, 170));
  }

  @Nullable
  private BufferedImage resolveIcon(SlotType slot) {
    Path override = ICON_DIR.resolve(slot.fileName());
    if (Files.isRegularFile(override)) {
      try {
        BufferedImage image = ImageIO.read(override.toFile());
        if (image != null) {
          return scaleDown(image);
        }
      } catch (IOException ex) {
        log.debug("Failed to load icon override {}", override, ex);
      }
    }
    return getBundledOrGeneratedIcon(slot);
  }

  @Nullable
  private BufferedImage getBundledOrGeneratedIcon(SlotType slot) {
    BufferedImage bundled = loadBundledIcon(slot);
    if (bundled != null) {
      return bundled;
    }
    return generatedDefaults.computeIfAbsent(slot, this::generateDefaultIcon);
  }

  @Nullable
  private static BufferedImage loadBundledIcon(SlotType slot) {
    return ImageUtil.loadImageResource(
        SlotDisplayService.class, "/icons/slots/" + slot.fileName());
  }

  private BufferedImage generateDefaultIcon(SlotType slot) {
    final int size = 32;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setColor(new Color(45, 45, 55));
    g.fillRoundRect(1, 1, size - 2, size - 2, 8, 8);
    g.setColor(new Color(170, 170, 190));
    g.drawRoundRect(1, 1, size - 2, size - 2, 8, 8);
    g.setColor(Color.WHITE);
    g.drawString(slot.getDefaultAbbrev(), 4, 20);
    g.dispose();
    return image;
  }

  private static BufferedImage scaleDown(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    if (width <= MAX_ICON_DIMENSION && height <= MAX_ICON_DIMENSION) {
      return image;
    }
    double scale =
        Math.min((double) MAX_ICON_DIMENSION / width, (double) MAX_ICON_DIMENSION / height);
    int newWidth = Math.max(1, (int) (width * scale));
    int newHeight = Math.max(1, (int) (height * scale));
    BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = scaled.createGraphics();
    g.drawImage(image, 0, 0, newWidth, newHeight, null);
    g.dispose();
    return scaled;
  }

  private void applyNameOverrides(Map<String, String> overrides) {
    for (SlotType slot : SlotType.values()) {
      String key = slot.name().toLowerCase();
      String altKey = slot.fileName().replace(".png", "");
      String value = overrides.get(key);
      if (value == null) {
        value = overrides.get(altKey);
      }
      if (value != null && !value.isEmpty()) {
        displayNames.put(slot, value);
      }
    }
  }
}
