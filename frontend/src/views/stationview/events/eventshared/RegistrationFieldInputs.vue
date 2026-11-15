/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EventFieldValueInput from './EventFieldValueInput.vue'
import {useAnswerMembers} from './useAnswerMembers'
import {missingAnswers, numberRange, type RegistrationAnswers} from './registrationAnswers'
import type {EventRegistrationField} from '@/api/generated/schema'

/**
 * The registration questions as a form asks them, for an appointment and for an event group alike: each
 * question under its label, a required one marked, a number question with its bounds below the input,
 * and the required ones still unanswered named at the end. The members a member question picks from are
 * fetched when the form appears.
 */
const props = defineProps<{
  fields: EventRegistrationField[]
  /** Where given, each input carries this prefix and the question id as its test id. */
  testIdPrefix?: string
}>()

const answers = defineModel<RegistrationAnswers>({required: true})

const {t} = useI18n()
const {allMembers, groupMembers, tagMembers, loadMembers} = useAnswerMembers(computed(() => props.fields))
const missing = computed(() => missingAnswers(props.fields, answers.value))

function set(fieldId: number, value: string) {
  answers.value = {...answers.value, [fieldId]: value}
}

function testIdOf(fieldId: number): string | undefined {
  return props.testIdPrefix ? `${props.testIdPrefix}-${fieldId}` : undefined
}

onMounted(loadMembers)
</script>

<template>
  <div class="space-y-3">
    <div v-for="field in fields" :key="field.id">
      <FieldLabel class="mb-1">
        {{ field.name }}
        <span v-if="field.config?.required" class="text-error">*</span>
      </FieldLabel>
      <EventFieldValueInput
          :model-value="answers[field.id] ?? ''"
          :field-type="field.fieldType"
          :config="field.config"
          :all-members="allMembers"
          :group-members="groupMembers"
          :tag-members="tagMembers"
          :data-testid="testIdOf(field.id)"
          @update:model-value="value => set(field.id, value)"
      />
      <p v-if="numberRange(field)" class="text-xs text-(--text-muted) mt-1">
        {{ t('events.registrationFields.range', numberRange(field)!) }}
      </p>
    </div>
    <p v-if="missing.length > 0" class="text-xs text-error">
      {{ t('events.registrationFields.missing', {fields: missing.map(field => field.name).join(', ')}) }}
    </p>
  </div>
</template>
