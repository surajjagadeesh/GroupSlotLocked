package com.gsl;

import com.gsl.model.LocalSlotState;
import com.gsl.model.SlotType;
import com.gsl.model.Violation;
import com.gsl.service.SlotValidator;
import java.util.Collections;
import java.util.EnumSet;
import org.junit.Assert;
import org.junit.Test;

public class SlotValidatorTest {
  @Test
  public void tooManyTokensSuspendsClaims() {
    LocalSlotState state =
        LocalSlotState.of(
            EnumSet.of(
                SlotType.HEAD,
                SlotType.CAPE,
                SlotType.NECK,
                SlotType.BODY,
                SlotType.LEGS,
                SlotType.BOOTS),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptyMap(),
            6);
    Assert.assertEquals(
        Violation.TOO_MANY_TOKENS,
        SlotValidator.validateEquip(state, EnumSet.of(SlotType.HEAD), 5, 5).getViolation());
  }

  @Test
  public void missingTokenBlocksEquip() {
    LocalSlotState state =
        LocalSlotState.of(
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptyMap(),
            0);
    Assert.assertEquals(
        Violation.NO_SLOT_CLAIM,
        SlotValidator.validateEquip(state, EnumSet.of(SlotType.HEAD), 5, 5).getViolation());
  }

  @Test
  public void validClaimAllowsEquip() {
    LocalSlotState state =
        LocalSlotState.of(
            EnumSet.of(SlotType.HEAD),
            Collections.emptySet(),
            Collections.emptySet(),
            Collections.emptyMap(),
            1);
    Assert.assertTrue(SlotValidator.validateEquip(state, EnumSet.of(SlotType.HEAD), 5, 5).isOk());
  }

  @Test
  public void overEquipLimitBlocksSixthSlot() {
    LocalSlotState state =
        LocalSlotState.of(
            EnumSet.allOf(SlotType.class),
            Collections.emptySet(),
            EnumSet.of(SlotType.HEAD, SlotType.CAPE, SlotType.NECK, SlotType.BODY, SlotType.LEGS),
            Collections.emptyMap(),
            5);
    Assert.assertEquals(
        Violation.OVER_EQUIP_LIMIT,
        SlotValidator.validateEquip(state, EnumSet.of(SlotType.BOOTS), 5, 5).getViolation());
  }

  @Test
  public void twoHandUsesTwoSlots() {
    LocalSlotState state =
        LocalSlotState.of(
            EnumSet.of(
                SlotType.MAIN_HAND, SlotType.OFF_HAND, SlotType.HEAD, SlotType.CAPE, SlotType.NECK),
            Collections.emptySet(),
            EnumSet.of(SlotType.HEAD, SlotType.CAPE, SlotType.NECK, SlotType.BODY, SlotType.LEGS),
            Collections.emptyMap(),
            3);
    EnumSet<SlotType> twoHand = EnumSet.of(SlotType.MAIN_HAND, SlotType.OFF_HAND);
    Assert.assertEquals(
        Violation.OVER_EQUIP_LIMIT,
        SlotValidator.validateEquip(state, twoHand, 5, 5).getViolation());
  }

  @Test
  public void simulateEquipReplacesTwoHandedWeapon() {
    EnumSet<SlotType> equipped = EnumSet.of(SlotType.HEAD, SlotType.MAIN_HAND, SlotType.OFF_HAND);
    EnumSet<SlotType> oneHand = EnumSet.of(SlotType.MAIN_HAND);
    EnumSet<SlotType> after = EnumSet.copyOf(SlotValidator.simulateEquip(equipped, oneHand));
    Assert.assertEquals(EnumSet.of(SlotType.HEAD, SlotType.MAIN_HAND), after);
  }
}
