/*
 * Copyright 2020 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.validator.client;

import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class ValidatorTimingActions implements ValidatorTimingChannel {
  private final ValidatorIndexProvider validatorIndexProvider;
  private final ValidatorTimingChannel blockDuties;
  private final ValidatorTimingChannel attestationDuties;
  private final ValidatorStatusLogger statusLogger;
  private final Spec spec;

  private final SettableGauge validatorCurrentEpoch;

  public ValidatorTimingActions(
      final ValidatorStatusLogger statusLogger,
      final ValidatorIndexProvider validatorIndexProvider,
      final ValidatorTimingChannel blockDuties,
      final ValidatorTimingChannel attestationDuties,
      final Spec spec,
      final MetricsSystem metricsSystem) {
    this.statusLogger = statusLogger;
    this.validatorIndexProvider = validatorIndexProvider;
    this.blockDuties = blockDuties;
    this.attestationDuties = attestationDuties;
    this.spec = spec;

    this.validatorCurrentEpoch =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.VALIDATOR,
            "current_epoch",
            "Current epoch of the validator client");
  }

  @Override
  public void onSlot(final UInt64 slot) {
    validatorIndexProvider.lookupValidators();
    blockDuties.onSlot(slot);
    attestationDuties.onSlot(slot);
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    validatorCurrentEpoch.set(epoch.doubleValue());
    final UInt64 firstSlotOfEpoch = spec.computeStartSlotAtEpoch(epoch);
    if (slot.equals(firstSlotOfEpoch.plus(1))) {
      statusLogger.checkValidatorStatusChanges();
    }
  }

  @Override
  public void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    blockDuties.onHeadUpdate(
        slot, previousDutyDependentRoot, currentDutyDependentRoot, headBlockRoot);
    attestationDuties.onHeadUpdate(
        slot, previousDutyDependentRoot, currentDutyDependentRoot, headBlockRoot);
  }

  @Override
  public void onChainReorg(final UInt64 newSlot, final UInt64 commonAncestorSlot) {
    blockDuties.onChainReorg(newSlot, commonAncestorSlot);
    attestationDuties.onChainReorg(newSlot, commonAncestorSlot);
  }

  @Override
  public void onPossibleMissedEvents() {
    blockDuties.onPossibleMissedEvents();
    attestationDuties.onPossibleMissedEvents();
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {
    blockDuties.onBlockProductionDue(slot);
    attestationDuties.onBlockProductionDue(slot);
  }

  @Override
  public void onAttestationCreationDue(final UInt64 slot) {
    attestationDuties.onAttestationCreationDue(slot);
    blockDuties.onAttestationCreationDue(slot);
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    attestationDuties.onAttestationAggregationDue(slot);
    blockDuties.onAttestationAggregationDue(slot);
  }
}
