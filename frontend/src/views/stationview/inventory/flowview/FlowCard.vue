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
import type {MovementFlow, StepRequest} from '@/api/movements'
import FlowStepRow from './FlowStepRow.vue'
import AddStepForm from './AddStepForm.vue'

const {t} = useI18n()

const props = defineProps<{
  flow: MovementFlow
  busy: boolean
}>()

const emit = defineEmits<{
  addStep: [flowId: number, step: StepRequest]
  archiveStep: [stepId: number]
  archiveFlow: [flowId: number]
  saveStep: [stepId: number, step: StepRequest]
  reorder: [flowId: number, stepIds: number[]]
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
</script>

<template>
  <NeutralContainer class="space-y-2" :class="props.flow.archived ? 'opacity-60' : ''">
    <div class="flex items-start justify-between gap-2">
      <div>
        <SubHeader>{{ props.flow.name }}</SubHeader>
        <div class="flex items-center gap-2 mt-1">
          <SecondaryBadge>{{ t(`movements.purpose.${props.flow.purpose}`) }}</SecondaryBadge>
          <SecondaryBadge v-if="props.flow.ownedByCluster">{{ t('flows.ownedByCluster') }}</SecondaryBadge>
          <SecondaryBadge v-if="props.flow.archived">{{ t('flows.archived') }}</SecondaryBadge>
        </div>
      </div>
      <div class="flex items-center gap-1">
        <MutedIconButton
            :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
            :label="t('flows.toggleSteps')"
            @click="expanded = !expanded"
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

    <Alert v-if="props.flow.problem && !props.flow.archived" variant="error">{{ props.flow.problem }}</Alert>

    <MutedText v-if="!expanded" size="sm" tag="div">
      {{ t('flows.stepCount', {count: props.flow.steps.filter(s => !s.archived).length}) }}
    </MutedText>

    <div v-else class="space-y-1">
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
