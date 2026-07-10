package com.gsl.ui;

import com.gsl.GroupSlotLockedConfig;
import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import com.gsl.service.SlotDisplayService;
import com.gsl.service.SlotStateService;
import com.gsl.service.SlotValidator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class GroupSlotLockedPanel extends PluginPanel {
  private final GroupSlotLockedConfig config;
  private final SlotStateService slotStateService;
  private final SlotDisplayService displayService;
  private final SlotValidator slotValidator;
  private final ItemManager itemManager;
  private final JLabel tokenCountLabel = new JLabel();
  private final JLabel equippedCountLabel = new JLabel();
  private final JLabel warningLabel = new JLabel();
  private final SlotCell[] cells = new SlotCell[SlotType.values().length];

  @Inject
  GroupSlotLockedPanel(
      GroupSlotLockedConfig config,
      SlotStateService slotStateService,
      SlotDisplayService displayService,
      SlotValidator slotValidator,
      ItemManager itemManager) {
    this.config = config;
    this.slotStateService = slotStateService;
    this.displayService = displayService;
    this.slotValidator = slotValidator;
    this.itemManager = itemManager;
    setLayout(new BorderLayout(0, 8));
    setBackground(ColorScheme.DARK_GRAY_COLOR);
    JPanel header = new JPanel(new GridLayout(2, 1));
    header.setBackground(ColorScheme.DARK_GRAY_COLOR);
    tokenCountLabel.setForeground(Color.WHITE);
    equippedCountLabel.setForeground(Color.WHITE);
    header.add(tokenCountLabel);
    header.add(equippedCountLabel);
    warningLabel.setForeground(Color.ORANGE);
    warningLabel.setHorizontalAlignment(SwingConstants.CENTER);
    JPanel grid = new JPanel(new GridLayout(3, 4, 4, 4));
    grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
    SlotType[] slots = SlotType.values();
    for (int i = 0; i < slots.length; i++) {
      cells[i] = new SlotCell(slots[i]);
      grid.add(cells[i]);
    }
    JPanel footer = new JPanel(new BorderLayout(0, 4));
    footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
    footer.add(warningLabel, BorderLayout.NORTH);
    JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
    actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
    JButton reloadIconsButton = new JButton("Reload icons");
    reloadIconsButton.addActionListener(
        e -> {
          displayService.reloadIcons();
          refresh(slotStateService.getState());
        });
    JButton openFolderButton = new JButton("Open icons folder");
    openFolderButton.addActionListener(e -> displayService.openIconFolder());
    actions.add(reloadIconsButton);
    actions.add(openFolderButton);
    footer.add(actions, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);
    add(grid, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
    slotStateService.addListener(this::refresh);
    refresh(slotStateService.getState());
  }

  private void refresh(LocalSlotState state) {
    tokenCountLabel.setText("Tokens: " + state.getHeldTokenCount() + "/" + config.maxHeldTokens());
    equippedCountLabel.setText(
        "Equipped: " + state.getEquippedSlots().size() + "/" + config.maxEquipped());
    boolean tooMany = state.getHeldTokenCount() > config.maxHeldTokens();
    warningLabel.setText(tooMany ? "Too many tokens held — store one in group storage" : " ");
    SlotType[] slots = SlotType.values();
    for (int i = 0; i < slots.length; i++) {
      cells[i].update(slots[i], state, tooMany);
    }
  }

  private class SlotCell extends JPanel {
    private final SlotType slot;
    private final JLabel iconLabel = new JLabel();
    private final JLabel nameLabel = new JLabel();

    SlotCell(SlotType slot) {
      this.slot = slot;
      setLayout(new BorderLayout());
      setPreferredSize(new Dimension(72, 56));
      setBackground(ColorScheme.DARKER_GRAY_COLOR);
      iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
      nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
      nameLabel.setForeground(Color.LIGHT_GRAY);
      nameLabel.setFont(nameLabel.getFont().deriveFont(10f));
      add(iconLabel, BorderLayout.CENTER);
      add(nameLabel, BorderLayout.SOUTH);
      installContextMenu();
    }

    private void installContextMenu() {
      JPopupMenu menu = new JPopupMenu();
      JMenuItem importItem = new JMenuItem("Import icon...");
      importItem.addActionListener(
          e -> {
            displayService.promptImportIcon(slot);
            refresh(slotStateService.getState());
          });
      JMenuItem resetItem = new JMenuItem("Reset icon");
      resetItem.addActionListener(
          e -> {
            try {
              displayService.resetIcon(slot);
              refresh(slotStateService.getState());
            } catch (IOException ex) {
              // display service logs at debug
            }
          });
      menu.add(importItem);
      menu.add(resetItem);
      MouseAdapter opener =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
              maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
              if (e.isPopupTrigger()) {
                menu.show(e.getComponent(), e.getX(), e.getY());
              }
            }
          };
      addMouseListener(opener);
      iconLabel.addMouseListener(opener);
      nameLabel.addMouseListener(opener);
    }

    void update(SlotType slot, LocalSlotState state, boolean tooMany) {
      int equippedItemId = state.getEquippedItemId(slot);
      BufferedImage icon;
      if (equippedItemId > 0) {
        icon = itemManager.getImage(equippedItemId);
      } else {
        icon = displayService.getIcon(slot);
      }
      if (icon != null) {
        iconLabel.setIcon(new javax.swing.ImageIcon(icon));
        iconLabel.setText(null);
      } else {
        iconLabel.setIcon(null);
        iconLabel.setText(displayService.getPanelAbbrev(slot));
      }
      nameLabel.setText(displayService.getPanelAbbrev(slot));
      nameLabel.setVisible(true);
      CellState cellState = resolveCellState(slot, state, tooMany);
      setBorder(BorderFactory.createLineBorder(cellState.borderColor, 2));
      setToolTipText(displayService.getDisplayName(slot) + " — " + cellState.label);
    }

    private CellState resolveCellState(SlotType slot, LocalSlotState state, boolean tooMany) {
      if (state.isEquipped(slot)) {
        return new CellState(Color.CYAN, "Equipped");
      }
      if (state.hasToken(slot) && tooMany) {
        return new CellState(Color.ORANGE, "Over token cap");
      }
      if (state.hasToken(slot) && slotValidator.hasActiveClaim(state, slot)) {
        return new CellState(Color.GREEN, "Available");
      }
      if (state.hasTokenInGroupStorage(slot) && !state.hasToken(slot)) {
        return new CellState(new Color(180, 140, 60), "In group storage");
      }
      return new CellState(Color.GRAY, "No token");
    }
  }

  private static final class CellState {
    private final Color borderColor;
    private final String label;

    private CellState(Color borderColor, String label) {
      this.borderColor = borderColor;
      this.label = label;
    }
  }
}
