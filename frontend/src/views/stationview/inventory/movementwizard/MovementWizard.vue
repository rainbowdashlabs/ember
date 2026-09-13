/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import WizardStep from './WizardStep.vue'
import WizardFooter from './WizardFooter.vue'
import {useMovementWizard, type WizardPrefill} from './useMovementWizard'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The one way to start a Vorgang.
 *
 * <p>Purpose, party, subject, reason, and then the chain those four resolve to, drawn before anything is
 * written. Opened from a screen that already knows some of the answers, it fills those in and asks only
 * what is left.
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
const error = ref('')

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
  error.value = ''
  await wizard.load()
  try {
    await wizard.start()
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  }
}, {immediate: true})

async function onNext() {
  error.value = ''
  try {
    await wizard.next()
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  }
}

async function start() {
  error.value = ''
  try {
    const id = await wizard.submit()
    if (id !== null) {
      model.value = false
      emit('started', id)
    }
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  }
}
</script>

<template>
  <Modal v-model="model">
    <div class="space-y-4" data-testid="movement-wizard">
      <SubHeader>{{ t('movements.wizard.title') }}</SubHeader>
      <MutedText size="sm">
        {{ t('movements.wizard.step', {current: wizard.position.value, total: wizard.total.value}) }}
      </MutedText>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <WizardStep :wizard="wizard"/>

      <WizardFooter
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
