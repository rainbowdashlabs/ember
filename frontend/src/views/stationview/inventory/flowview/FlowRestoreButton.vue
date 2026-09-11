/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {movements} from '@/api'
import type {FlowStepMapping, RestorePlan} from '@/api/movements'
import {useFlowProblems} from '@/composables/useFlowProblems'
import FlowRestoreMappingList from './FlowRestoreMappingList.vue'

/**
 * Puts a chain back to the preset for the combination it serves, once somebody has said so.
 *
 * <p>Behind a question because the steps are replaced rather than added to, and a chain a station has
 * worked on for a year looks exactly like one it has not touched.
 *
 * <p>The question is asked with the plan in hand: a movement standing on a step the restore removes
 * has to be moved, and where the answer is not certain the reader decides rather than being told
 * afterwards where their movements went.
 */
const {t} = useI18n()
const {refusalText} = useFlowProblems()

const props = defineProps<{
  flowId: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  confirm: [mappings: FlowStepMapping[]]
  /** Why the plan could not be read, worded for the card that shows it. */
  refused: [message: string]
}>()

const asking = ref(false)
const plan = ref<RestorePlan | null>(null)
const choices = ref<Record<number, number | null>>({})

/** A plan read once per asking, so a dialog opened again never answers from a chain that has moved on. */
async function ask() {
  asking.value = true
  plan.value = null
  choices.value = {}
  emit('refused', '')
  try {
    const read = await movements.restorePlan(props.flowId)
    const suggested: Record<number, number | null> = {}
    for (const landing of read.movements) suggested[landing.stepId ?? -1] = landing.suggestedIndex
    choices.value = suggested
    plan.value = read
  } catch (e) {
    asking.value = false
    emit('refused', refusalText(e))
  }
}

function choose(stepId: number | null, stepIndex: number | null) {
  choices.value = {...choices.value, [stepId ?? -1]: stepIndex}
}

const complete = computed(() =>
    plan.value !== null && plan.value.movements.every(landing => choices.value[landing.stepId ?? -1] != null)
)

/** A wide dialog only where there is a list in it. The bare question is one sentence and a button. */
const size = computed(() => (plan.value && plan.value.movements.length > 0 ? 'lg' : 'sm'))

/**
 * Sends every step's landing, the pre-filled ones included, so the chain is rewritten exactly as the
 * dialog showed it.
 */
function confirm() {
  const read = plan.value
  if (!read || !complete.value) return
  asking.value = false
  emit(
      'confirm',
      read.movements.map(landing => ({
        stepId: landing.stepId,
        stepIndex: choices.value[landing.stepId ?? -1] as number,
      }))
  )
}
</script>

<template>
  <MutedIconButton
      :disabled="disabled"
      :icon="['fas', 'rotate-left']"
      :label="t('flows.restore')"
      @click="ask"
  />

  <Modal v-model="asking" :size="size">
    <div class="space-y-4">
      <SubHeader>{{ t('flows.restoreTitle') }}</SubHeader>
      <p class="text-sm">{{ t('flows.restoreConfirm') }}</p>

      <Spinner v-if="!plan"/>
      <FlowRestoreMappingList
          v-else-if="plan.movements.length > 0"
          :choices="choices"
          :movements="plan.movements"
          :steps="plan.steps"
          @choose="choose"
      />

      <div class="flex justify-end gap-3">
        <SecondaryButton data-cancel @click="asking = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!complete" data-confirm @click="confirm">
          {{ t('flows.restoreAction') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
