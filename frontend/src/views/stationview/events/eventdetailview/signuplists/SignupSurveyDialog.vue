/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SignupSetNotes from './SignupSetNotes.vue'
import type {SignupMemberSet} from '@/composables/useSignupMemberSet'

/**
 * Names the survey before it is made, and says plainly what it will and will not be yet.
 *
 * <p>A survey is not usable the moment it opens. It is created as a draft with no questions in it,
 * the people it is meant for are set in a second step, and nothing reaches any of them until it is
 * published. Promising otherwise here would send somebody away expecting a survey to be running.
 */
const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  creating: boolean
  error: string
  memberSet: SignupMemberSet
  /** The evening the set belongs to, already written the way a reader reads a date. */
  dateLabel: string
  /** What the survey is called before anybody changes it: the appointment and its date. */
  suggestedName: string
}>()

const emit = defineEmits<{
  (e: 'submit', payload: {name: string}): void
}>()

const {t} = useI18n()

const name = ref('')

watch(visible, opened => {
  if (opened) name.value = props.suggestedName
}, {immediate: true})

function submit() {
  if (!name.value.trim()) return
  emit('submit', {name: name.value.trim()})
}
</script>

<template>
  <Modal v-model="visible" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('signupLists.surveyTitle') }}</SubHeader>

      <div>
        <FieldLabel>{{ t('signupLists.surveyName') }}</FieldLabel>
        <TextInput v-model="name" data-testid="signup-survey-name"/>
      </div>

      <SignupSetNotes :member-set="memberSet" :date-label="dateLabel"/>

      <Alert variant="info">{{ t('signupLists.surveyDraft') }}</Alert>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="visible = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="creating || !name.trim()" @click="submit">
          {{ t('common.create') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
