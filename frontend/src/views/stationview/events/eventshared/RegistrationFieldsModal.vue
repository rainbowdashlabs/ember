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
import {EventFieldTypes, type EventRegistrationField, type RegistrationFieldValue} from '@/api/events'
import {stationMembers as stationMembersApi} from '@/api'
import type {StationMember} from '@/api/types'

const props = defineProps<{
  fields: EventRegistrationField[]
  title?: string
  busy?: boolean
  error?: string
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [values: RegistrationFieldValue[]]
}>()

const {t} = useI18n()

const answers = ref<Record<number, string>>({})
const allMembers = ref<StationMember[]>([])

const needsMembers = computed(() => props.fields.some(f => f.fieldType.startsWith('MEMBER')))

const missing = computed(() =>
    props.fields.filter(f => f.config?.required && !(answers.value[f.id] ?? '').trim()))

/**
 * Seeds the form with the configured defaults every time it opens. A default is what the member
 * starts from, not an answer already given on their behalf.
 */
function seed() {
  const seeded: Record<number, string> = {}
  for (const field of props.fields) {
    seeded[field.id] = field.config?.defaultValue ?? ''
  }
  answers.value = seeded
}

async function loadMembers() {
  if (!needsMembers.value || allMembers.value.length > 0) return
  try {
    allMembers.value = await stationMembersApi.listMembers()
  } catch {
    allMembers.value = []
  }
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
      <PrimaryButton :disabled="busy || missing.length > 0" @click="confirm">
        {{ t('events.register') }}
      </PrimaryButton>
    </div>
  </Modal>
</template>
