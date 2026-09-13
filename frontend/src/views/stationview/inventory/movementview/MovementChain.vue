/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, toRef} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FlowDiagram from '@/components/movement/FlowDiagram.vue'
import MovementStep from './MovementStep.vue'
import MovementActionPanel, {type AcknowledgePayload} from './MovementActionPanel.vue'
import MovementRechainButton from './MovementRechainButton.vue'
import {useMovementParties} from '@/composables/useMovementParties'
import type {InventorySize} from '@/api/inventory'
import type {MovementDetail} from '@/api/movements'

/**
 * The chain a movement walks: drawn, then listed step by step, with the answer to the step it stands
 * on offered on that step itself.
 *
 * <p>Moving it across to another chain is offered only where it is on one it no longer belongs to.
 * A button that is always there for a case that almost never arises reads as something a person is
 * expected to consider every time.
 */
const props = defineProps<{
  detail: MovementDetail
  open: boolean
  isManager: boolean
  busy: boolean
  /** The sizes of the movement's inventory, for a piece written down on the spot. */
  sizes: InventorySize[]
  /** Whether the arriving piece may be written down here rather than picked. */
  mayRecord: boolean
  error: string
}>()

const emit = defineEmits<{
  acknowledge: [payload: AcknowledgePayload]
  force: [payload: AcknowledgePayload]
  decline: [reason: string]
  cancel: [reason: string]
  rechain: [stepIndex: number | null]
  refused: [message: string]
}>()

const {t} = useI18n()

const movement = computed(() => props.detail.movement)
const {ownerLabel} = useMovementParties(movement)

/** Retired steps only belong on the chain when this movement actually walked through one. */
const visibleSteps = computed(() =>
    props.detail.steps.filter(step => !step.archived || step.ackKind || step.current))

/**
 * The same steps as the drawing needs them, with the ones already acknowledged marked as walked.
 *
 * <p>The chain draws what this movement is doing rather than what the station configured, so a step
 * retired after this movement passed through it still belongs on the picture.
 */
const drawnSteps = computed(() =>
    visibleSteps.value.map(step => ({...step, archived: false, walked: !!step.ackKind})))
</script>

<template>
  <div>
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ t('movements.chain') }}</SubHeader>
      <MovementRechainButton
          v-if="props.open && props.isManager && movement.belongsOnAnotherFlow"
          :disabled="props.busy"
          :movement-id="movement.id"
          @confirm="stepIndex => emit('rechain', stepIndex)"
          @refused="message => emit('refused', message)"
      />
    </div>

    <Alert v-if="props.error" variant="error" class="mb-2">{{ props.error }}</Alert>

    <FlowDiagram
        :owner-kind="movement.ownerKind"
        :owner-name="movement.ownerName"
        :steps="drawnSteps"
        class="mb-2"
    />

    <MovementStep
        v-for="step in visibleSteps"
        :key="step.id"
        :open="props.open"
        :owner-label="ownerLabel"
        :step="step"
    >
      <template #action>
        <MovementActionPanel
            v-if="step.current && props.open"
            :busy="props.busy"
            :can-force="props.isManager"
            :inventory-id="movement.inventoryId"
            :inventory-name="movement.inventoryName"
            :inventory-type="movement.inventoryType"
            :may-record="props.mayRecord"
            :owner-cluster-id="movement.ownerClusterId"
            :owner-kind="movement.ownerKind"
            :sizes="props.sizes"
            :step="step"
            :wanted-size-id="movement.newSizeId ?? movement.oldSizeId"
            @acknowledge="payload => emit('acknowledge', payload)"
            @force="payload => emit('force', payload)"
            @decline="reason => emit('decline', reason)"
            @cancel="reason => emit('cancel', reason)"
        />
      </template>
    </MovementStep>
  </div>
</template>
