package com.gsl.model;

import java.awt.Color;

public enum TokenIconStyle {
  NORMAL(1.0f, new Color(30, 30, 36, 255)),
  PLACEHOLDER(0.55f, new Color(30, 30, 36, 255));

  private final float iconAlpha;
  private final Color background;

  TokenIconStyle(float iconAlpha, Color background) {
    this.iconAlpha = iconAlpha;
    this.background = background;
  }

  public float getIconAlpha() {
    return iconAlpha;
  }

  public Color getBackground() {
    return background;
  }
}
