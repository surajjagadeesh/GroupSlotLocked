package com.gsl.model;

import javax.annotation.Nullable;
import lombok.Value;

@Value
public class ValidationResult {
  Violation violation;
  @Nullable SlotType slot;

  public static ValidationResult ok() {
    return new ValidationResult(Violation.NONE, null);
  }

  public static ValidationResult fail(Violation violation, @Nullable SlotType slot) {
    return new ValidationResult(violation, slot);
  }

  public boolean isOk() {
    return violation == Violation.NONE;
  }
}
