/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {movements} from '@/api'
import type {RechainPlan} from '@/api/movements'
import {apiErrorMessage} from '@/util/apiError'
import MovementRechainPlan from './MovementRechainPlan.vue'

/**
 * Moves a movement onto the chain it belongs on, once somebody has said where it is to stand.
 *
 * <p>A movement is given its chain when it sets out and never again, so one that set out on the
 * wrong one walks steps written for somebody else's gear. Forcing those through or calling the
 * movement off both write a history that did not happen, which is what this exists instead of.
 *
 * <p>The plan is read when the dialog opens rather than held, so a dialog opened a second time
 * never answers from a chain that has since changed.
 */
const {t} = useI18n()

const props = defineProps<{
  movementId: number
  disabled?: boolean
}>()

const emit = defineEmits<{
  confirm: [stepIndex: number]
  /** Why the plan could not be read, worded for the panel the view shows its failures in. */
  refused: [message: string]
}>()

const asking = ref(false)
const plan = ref<RechainPlan | null>(null)
const stepIndex = ref<number | null>(null)

async function ask() {
  asking.value = true
  plan.value = null
  stepIndex.value = null
  emit('refused', '')
  try {
    const read = await movements.rechainPlan(props.movementId)
    stepIndex.value = read.suggestedIndex
    plan.value = read
  } catch (e) {
    asking.value = false
    emit('refused', apiErrorMessage(e) ?? t('common.error'))
  }
}

function choose(index: number | null) {
  stepIndex.value = index
}

function confirm() {
  if (stepIndex.value === null) return
  asking.value = false
  emit('confirm', stepIndex.value)
}
</script>

<template>
  <SecondaryButton
      :disabled="props.disabled"
      :icon="['fas', 'right-left']"
      data-testid="movement-rechain"
      @click="ask"
  >
    {{ t('movements.rechain.action') }}
  </SecondaryButton>

  <Modal v-model="asking" size="md">
    <div class="space-y-4">
      <SubHeader>{{ t('movements.rechain.title') }}</SubHeader>

      <Spinner v-if="!plan"/>

      <div v-else-if="plan.alreadyRight" class="space-y-4">
        <p class="text-sm">{{ t('movements.rechain.alreadyRight') }}</p>
        <div class="flex justify-end">
          <SecondaryButton data-cancel @click="asking = false">{{ t('common.close') }}</SecondaryButton>
        </div>
      </div>

      <div v-else class="space-y-4">
        <MovementRechainPlan :plan="plan" :step-index="stepIndex" @choose="choose"/>
        <div class="flex justify-end gap-3">
          <SecondaryButton data-cancel @click="asking = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="stepIndex === null" data-confirm @click="confirm">
            {{ t('movements.rechain.confirm') }}
          </PrimaryButton>
        </div>
      </div>
    </div>
  </Modal>
</template>
