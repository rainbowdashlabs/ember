/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PublicConsentCheckbox from '@/components/public/PublicConsentCheckbox.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import GuardianFields from './GuardianFields.vue'
import DynamicFieldInput from './DynamicFieldInput.vue'
import type { GuardianInput, WaitingListInviteInfo } from '@/api/waitingList'

const props = defineProps<{
  inviteInfo: WaitingListInviteInfo
  guardians: GuardianInput[]
  submitting: boolean
  fieldValueOf: (fieldId: number) => string
}>()
const emit = defineEmits<{
  (e: 'add-guardian'): void
  (e: 'remove-guardian', index: number): void
  (e: 'set-field-value', fieldId: number, value: string): void
  (e: 'submit'): void
}>()

const firstname = defineModel<string>('firstname', {required: true})
const lastname = defineModel<string>('lastname', {required: true})
const notes = defineModel<string>('notes', {required: true})
const consentAccepted = defineModel<boolean>('consentAccepted', {required: true})
const consentVersion = defineModel<string>('consentVersion', {required: true})
const privacyVersion = defineModel<string>('privacyVersion', {required: true})
const tosVersion = defineModel<string>('tosVersion', {required: true})

const { t } = useI18n()
</script>

<template>
  <form class="space-y-4" @submit.prevent="emit('submit')">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
      <TextInput v-model="firstname" :placeholder="t('waitingList.register.firstnamePlaceholder')" />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
      <TextInput v-model="lastname" :placeholder="t('waitingList.register.lastnamePlaceholder')" />
    </div>

    <GuardianFields
      :guardians="props.guardians"
      @add="emit('add-guardian')"
      @remove="emit('remove-guardian', $event)"
    />

    <DynamicFieldInput
      v-for="field in props.inviteInfo.fields"
      :key="field.id"
      :field="field"
      :value="props.fieldValueOf(field.id)"
      @update:value="emit('set-field-value', field.id, $event)"
    />

    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
      <TextAreaInput v-model="notes" :placeholder="t('waitingList.register.notesPlaceholder')" />
    </div>

    <NeutralContainer>
      <PublicConsentCheckbox
        v-model:accepted="consentAccepted"
        v-model:consent-version="consentVersion"
        v-model:privacy-version="privacyVersion"
        v-model:tos-version="tosVersion"
      />
    </NeutralContainer>

    <PrimaryButton :disabled="props.submitting || !consentAccepted" class="w-full" type="submit">
      {{ props.submitting ? t('common.loading') : t('waitingList.register.submit') }}
    </PrimaryButton>
  </form>
</template>
