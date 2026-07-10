package com.gsl.model;

/** Where the token icon is being drawn, which controls press/dim styling. */
public enum TokenIconRenderContext {
  /** Resting icon in inventory/bank. */
  STATIC,
  /** Click-hold cover on the source slot before movement (subtle press dim). */
  PRESSED_IN_SLOT,
  /** Undimmed helmet over the source slot during drag (cape cover only). */
  SOURCE_LAYER_COVER,
  /** Drag ghost following the cursor (vanilla uses full-brightness sprite). */
  DRAG_AT_CURSOR
}
