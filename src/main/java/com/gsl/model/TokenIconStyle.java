package com.gsl.model;

import java.awt.Color;

public enum TokenIconStyle {
  /** Full-brightness icon, same as a normal bank/inventory item. */
  NORMAL(1.0f),
  /** Slightly dimmed placeholder icon at rest; unchanged while clicked or dragged. */
  PLACEHOLDER(0.78f),
  /** Pressed/dragging dim for real items — held for the full click-hold and drag. */
  PRESSED(0.85f);

  private static final Color SLOT_BACKGROUND = new Color(30, 30, 36, 255);

  private final float luminanceScale;

  TokenIconStyle(float luminanceScale) {
    this.luminanceScale = luminanceScale;
  }

  public float getLuminanceScale() {
    return luminanceScale;
  }

  public Color getBackground() {
    return SLOT_BACKGROUND;
  }

  public static TokenIconStyle forContext(TokenIconRenderContext context, boolean placeholder) {
    if (placeholder) {
      return PLACEHOLDER;
    }
    switch (context) {
      case PRESSED_IN_SLOT:
      case SOURCE_LAYER_COVER:
      case DRAG_AT_CURSOR:
        return PRESSED;
      case STATIC:
      default:
        return NORMAL;
    }
  }
}
