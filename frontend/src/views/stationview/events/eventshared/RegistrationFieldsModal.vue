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
import type {Failure} from '@/util/failure'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RegistrationFieldInputs from './RegistrationFieldInputs.vue'
import {answersToSend, missingAnswers, seedAnswers, type RegistrationAnswers} from './registrationAnswers'
import type {EventRegistrationFieldValue, EventRegistrationField} from '@/api/generated/schema'

const props = defineProps<{
  fields: EventRegistrationField[]
  /** The answers already on file, where an existing registration is being corrected. */
  values?: EventRegistrationFieldValue[]
  title?: string
  /** What the confirming button says, for a dialog that changes an answer rather than gives one. */
  confirmLabel?: string
  busy?: boolean
  error?: string
  /** The same thing described, for a caller that has one: guidance and the offer to report. */
  failure?: Failure | null
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [values: EventRegistrationFieldValue[]]
}>()

const {t} = useI18n()

const answers = ref<RegistrationAnswers>({})

const missing = computed(() => missingAnswers(props.fields, answers.value))

function confirm() {
  if (missing.value.length > 0) return
  emit('confirm', answersToSend(props.fields, answers.value))
}

/** Seeds the form every time it opens, from the answers on file and the configured defaults. */
watch(show, (open) => {
  if (open) answers.value = seedAnswers(props.fields, props.values)
}, {immediate: true})
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-3">{{ title ?? t('events.registrationFields.title') }}</SubHeader>

    <FailureAlert :failure="failure" :message="error" class="mb-3"/>

    <RegistrationFieldInputs v-model="answers" :fields="fields"/>

    <ButtonRow pair align="end" class="mt-4">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton data-onboarding="events.registration-fields.submit" :disabled="busy || missing.length > 0" @click="confirm">
        {{ confirmLabel ?? t('events.register') }}
      </PrimaryButton>
    </ButtonRow>
  </Modal>
</template>
