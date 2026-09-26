/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import WizardStep from './WizardStep.vue'
import WizardFooter from './WizardFooter.vue'
import {useMovementWizard, type WizardPrefill} from './useMovementWizard'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The one way to start a movement.
 *
 * <p>Purpose, party, subject, reason, and then the chain those four resolve to, drawn before anything is
 * written. Opened from a screen that already knows some of the answers, it fills those in and asks only
 * what is left.
 *
 * <p>It keeps a height of its own rather than shrinking to the question on screen: the menus a step
 * opens hang below their field inside a dialogue that scrolls, and a dialogue as short as the question
 * cut the list of members off at the second name.
 */
const props = defineProps<{
  /** What the opening screen already knows, and which questions it has therefore answered. */
  prefill?: WizardPrefill
}>()

const emit = defineEmits<{
  started: [movementId: number]
}>()

const model = defineModel<boolean>({required: true})

const {t} = useI18n()

const wizard = useMovementWizard(() => props.prefill ?? {})
const failure = ref<Failure | null>(null)

const isLast = computed(() => wizard.step.value === 'preview')
const canGoOn = computed(() => {
  switch (wizard.step.value) {
    case 'purpose':
      return wizard.purpose.value !== null
    case 'party':
      return wizard.memberId.value !== null || wizard.forTheStore.value
    case 'subject':
      return wizard.canLeaveSubject.value
    default:
      return true
  }
})

watch(model, async open => {
  if (!open) return
  failure.value = null
  await wizard.load()
  try {
    await wizard.start()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}, {immediate: true})

async function onNext() {
  failure.value = null
  try {
    await wizard.next()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

async function start() {
  failure.value = null
  try {
    const id = await wizard.submit()
    if (id !== null) {
      model.value = false
      emit('started', id)
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <Modal v-model="model" size="lg">
    <div class="flex min-h-[26rem] flex-col gap-4" data-testid="movement-wizard">
      <SubHeader>{{ t('movements.wizard.title') }}</SubHeader>
      <MutedText size="sm">
        {{ t('movements.wizard.step', {current: wizard.position.value, total: wizard.total.value}) }}
      </MutedText>

      <FailureAlert :failure="failure"/>

      <WizardStep :wizard="wizard"/>

      <WizardFooter
          class="mt-auto"
          :busy="wizard.busy.value"
          :can-go-on="canGoOn"
          :has-preview="wizard.preview.value !== null"
          :last="isLast"
          :position="wizard.position.value"
          @back="wizard.back"
          @cancel="model = false"
          @next="onNext"
          @start="start"
      />
    </div>
  </Modal>
</template>
