/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {FlowStepMapping, MovementFlow, MovementFlowBinding, StepRequest} from '@/api/movements'
import FlowDiagram from '@/components/movement/FlowDiagram.vue'
import FlowStepRow from './FlowStepRow.vue'
import AddStepForm from './AddStepForm.vue'
import FlowBindingLine from './FlowBindingLine.vue'
import FlowRestoreButton from './FlowRestoreButton.vue'
import {useFlowProblems} from '@/composables/useFlowProblems'

const {t} = useI18n()
const {problemText} = useFlowProblems()

const props = defineProps<{
  flow: MovementFlow
  busy: boolean
  /** Why the last change to this chain was refused, shown where the change was made. */
  error?: string
  /** The combination this chain serves, absent for one the station wrote itself. */
  binding?: MovementFlowBinding
}>()

const emit = defineEmits<{
  addStep: [flowId: number, step: StepRequest]
  archiveStep: [stepId: number]
  archiveFlow: [flowId: number]
  saveStep: [stepId: number, step: StepRequest]
  reorder: [flowId: number, stepIds: number[]]
  restore: [flowId: number, mappings: FlowStepMapping[]]
  /** A refusal the restore dialog read for itself, shown where every other refusal about this chain is. */
  restoreRefused: [flowId: number, message: string]
}>()

/** Only the live steps carry an order. A retired one keeps its place and is not moved about. */
const liveSteps = computed(() => props.flow.steps.filter(step => !step.archived))

/**
 * Swaps a step with its neighbour and sends the whole order.
 *
 * <p>The whole order rather than the pair, because two calls in flight would leave the chain reading
 * as something nobody wrote.
 */
function move(stepId: number, direction: -1 | 1) {
  const ids = liveSteps.value.map(step => step.id)
  const from = ids.indexOf(stepId)
  const to = from + direction
  if (from < 0 || to < 0 || to >= ids.length) return
  ;[ids[from], ids[to]] = [ids[to]!, ids[from]!]
  emit('reorder', props.flow.id, ids)
}

const expanded = ref(false)

/**
 * A flow the body above the station owns is shown and named here, and left alone: the owner sets its
 * own terms and the station does not edit them.
 */
const editable = computed(() => !props.flow.ownedByCluster && !props.flow.archived)

/**
 * Only a chain that stands for a combination can be put back, since the preset is written for the
 * combination and not for the chain. One the station wrote itself stands for nothing and is left alone.
 */
const restorable = computed(() => editable.value && props.binding !== undefined)

/**
 * What is worth saying about a chain besides its name.
 *
 * <p>The purpose is one of them only where the combination is not shown, since that line already
 * carries it and a card need not say the same thing twice.
 */
const marks = computed(() =>
    [
      {
        key: 'purpose',
        shown: props.binding === undefined,
        label: t(`movements.purpose.${props.flow.purpose}`),
      },
      {key: 'ownedByCluster', shown: props.flow.ownedByCluster, label: t('flows.ownedByCluster')},
      {key: 'archived', shown: props.flow.archived, label: t('flows.archived')},
    ].filter(mark => mark.shown)
)
</script>

<template>
  <NeutralContainer class="space-y-2" :class="props.flow.archived ? 'opacity-60' : ''">
    <div class="flex items-start justify-between gap-2">
      <div class="space-y-1">
        <SubHeader>{{ props.flow.name }}</SubHeader>
        <FlowBindingLine v-if="props.binding" :binding="props.binding"/>
        <div v-if="marks.length > 0" class="flex items-center gap-2">
          <SecondaryBadge v-for="mark in marks" :key="mark.key">{{ mark.label }}</SecondaryBadge>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <MutedIconButton
            :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
            :label="t('flows.toggleSteps')"
            @click="expanded = !expanded"
        />
        <FlowRestoreButton
            v-if="restorable"
            :disabled="props.busy"
            :flow-id="props.flow.id"
            @confirm="mappings => emit('restore', props.flow.id, mappings)"
            @refused="message => emit('restoreRefused', props.flow.id, message)"
        />
        <MutedIconButton
            v-if="editable"
            :icon="['fas', 'xmark']"
            :label="t('flows.archiveFlow')"
            hover="error"
            @click="emit('archiveFlow', props.flow.id)"
        />
      </div>
    </div>

    <Alert v-if="props.error" variant="error" data-testid="flow-error">{{ props.error }}</Alert>

    <Alert v-if="props.flow.problem && !props.flow.archived" variant="error" data-testid="flow-problem">
      {{ problemText(props.flow.problem) }}
    </Alert>

    <MutedText v-if="!expanded" size="sm" tag="div">
      {{ t('flows.stepCount', {count: props.flow.steps.filter(s => !s.archived).length}) }}
    </MutedText>

    <div v-else class="space-y-1">
      <FlowDiagram :owner-kind="props.binding?.ownerKind" :steps="props.flow.steps"/>
      <FlowStepRow
          v-for="step in props.flow.steps"
          :key="step.id"
          :can-move-down="liveSteps.findIndex(s => s.id === step.id) < liveSteps.length - 1"
          :can-move-up="liveSteps.findIndex(s => s.id === step.id) > 0"
          :editable="editable"
          :step="step"
          @archive="emit('archiveStep', step.id)"
          @save="updated => emit('saveStep', step.id, updated)"
          @move="direction => move(step.id, direction)"
      />
      <AddStepForm v-if="editable" :busy="props.busy" @add="step => emit('addStep', props.flow.id, step)"/>
    </div>
  </NeutralContainer>
</template>
