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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import {useAnswerMembers} from './useAnswerMembers'
import {EventFieldTypes, type EventRegistrationField, type RegistrationFieldValue} from '@/api/events'

const props = defineProps<{
  fields: EventRegistrationField[]
  /** The answers already on file, where an existing registration is being corrected. */
  values?: RegistrationFieldValue[]
  title?: string
  /** What the confirming button says, for a dialog that changes an answer rather than gives one. */
  confirmLabel?: string
  busy?: boolean
  error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [values: RegistrationFieldValue[]]
}>()

const {t} = useI18n()

const answers = ref<Record<number, string>>({})

const {allMembers, loadMembers} = useAnswerMembers(computed(() => props.fields))

const missing = computed(() =>
    props.fields.filter(f => f.config?.required && !(answers.value[f.id] ?? '').trim()))

/**
 * Seeds the form every time it opens: with the answers already on file where there are any, and
 * with the configured defaults where there are none. A default is what the member starts from, not
 * an answer already given on their behalf, so it never stands in front of one that was.
 */
function seed() {
  const onFile = new Map((props.values ?? []).map(value => [value.fieldId, value.value]))
  const seeded: Record<number, string> = {}
  for (const field of props.fields) {
    seeded[field.id] = onFile.get(field.id) ?? field.config?.defaultValue ?? ''
  }
  answers.value = seeded
}

function answerOf(fieldId: number): string {
  return answers.value[fieldId] ?? ''
}

function confirm() {
  if (missing.value.length > 0) return
  emit('confirm', props.fields
      .map(field => ({fieldId: field.id, value: answers.value[field.id] ?? ''}))
      .filter(entry => entry.value.trim() !== ''))
}

watch(show, (open) => {
  if (!open) return
  seed()
  loadMembers()
}, {immediate: true})
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-3">{{ title ?? t('events.registrationFields.title') }}</SubHeader>

    <Alert v-if="error" variant="error" class="mb-3">{{ error }}</Alert>

    <div class="space-y-3">
      <div v-for="field in fields" :key="field.id">
        <FieldLabel class="mb-1">
          {{ field.name }}
          <span v-if="field.config?.required" class="text-error">*</span>
        </FieldLabel>
        <EventFieldValueInput
            :model-value="answerOf(field.id)"
            :field-type="field.fieldType"
            @update:model-value="v => { answers[field.id] = v }"
            :config="field.config as Record<string, unknown>"
            :all-members="allMembers"
        />
        <p
            v-if="field.fieldType === EventFieldTypes.NUMBER && (field.config?.min != null || field.config?.max != null)"
            class="text-xs text-(--text-muted) mt-1"
        >
          {{ t('events.registrationFields.range', {min: field.config?.min ?? '–', max: field.config?.max ?? '–'}) }}
        </p>
      </div>
    </div>

    <p v-if="missing.length > 0" class="text-xs text-error mt-3">
      {{ t('events.registrationFields.missing', {fields: missing.map(f => f.name).join(', ')}) }}
    </p>

    <div class="flex justify-end gap-2 mt-4">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton data-onboarding="events.registration-fields.submit" :disabled="busy || missing.length > 0" @click="confirm">
        {{ confirmLabel ?? t('events.register') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
