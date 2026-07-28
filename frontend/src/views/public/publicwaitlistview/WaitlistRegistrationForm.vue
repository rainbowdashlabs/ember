/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import WaitlistGuardiansSection from './WaitlistGuardiansSection.vue'
import WaitlistCustomField from './WaitlistCustomField.vue'
import type {GuardianInput, PublicWaitlistFormResponse, WaitingListField} from '@/api/waitingList'

defineProps<{
  form: PublicWaitlistFormResponse
  guardians: GuardianInput[]
  fieldValues: Record<number, string>
  canSubmit: boolean
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'add-guardian'): void
  (e: 'remove-guardian', index: number): void
  (e: 'set-field', field: WaitingListField, value: string): void
  (e: 'submit'): void
}>()

const firstname = defineModel<string>('firstname', {default: ''})
const lastname = defineModel<string>('lastname', {default: ''})
const email = defineModel<string>('email', {default: ''})
const notes = defineModel<string>('notes', {default: ''})
const consentAccepted = defineModel<boolean>('consentAccepted', {default: false})
const consentVersion = defineModel<string>('consentVersion', {default: ''})
const privacyVersion = defineModel<string>('privacyVersion', {default: ''})
const tosVersion = defineModel<string>('tosVersion', {default: ''})

const {t} = useI18n()

function fieldValue(field: WaitingListField, values: Record<number, string>): string {
  return values[field.id] ?? ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FormLabel>{{ t('waitingList.publicRegistration.firstname') }} *</FormLabel>
        <TextInput v-model="firstname"/>
      </div>
      <div class="space-y-1">
        <FormLabel>{{ t('waitingList.publicRegistration.lastname') }}</FormLabel>
        <TextInput v-model="lastname"/>
      </div>
    </div>
    <div class="space-y-1">
      <FormLabel>{{ t('waitingList.publicRegistration.email') }} *</FormLabel>
      <TextInput v-model="email" type="email"/>
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.publicRegistration.emailHint') }}</p>
    </div>

    <WaitlistGuardiansSection
        :guardians="guardians"
        @add="emit('add-guardian')"
        @remove="emit('remove-guardian', $event)"
    />

    <template v-if="form.fields.length > 0">
      <WaitlistCustomField
          v-for="field in form.fields"
          :key="field.id"
          :field="field"
          :value="fieldValue(field, fieldValues)"
          @update="emit('set-field', field, $event)"
      />
    </template>

    <div class="space-y-1">
      <FormLabel>{{ t('waitingList.publicRegistration.notes') }}</FormLabel>
      <TextAreaInput v-model="notes" :placeholder="t('waitingList.publicRegistration.notesPlaceholder')"/>
    </div>

    <PublicConsentCheckbox
        v-model:accepted="consentAccepted"
        v-model:consent-version="consentVersion"
        v-model:privacy-version="privacyVersion"
        v-model:tos-version="tosVersion"
        class="block mt-4"/>

    <PrimaryButton :disabled="!canSubmit || submitting" class="w-full mt-4" @click="emit('submit')">
      {{ submitting ? t('common.loading') : t('waitingList.publicRegistration.submit') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
