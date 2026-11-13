/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EventFieldValueInput from '../eventshared/EventFieldValueInput.vue'
import {useAnswerMembers} from '../eventshared/useAnswerMembers'
import type {EventRegistrationField} from '@/api/generated/schema'
import {missingAnswers} from './courseAnswers'

/**
 * The questions of a course as the sign-up asks them, once for every part chosen. Required questions
 * carry a marker, and the ones still unanswered are named below.
 */
const props = defineProps<{
  fields: EventRegistrationField[]
}>()

const answers = defineModel<Record<number, string>>({required: true})

const {t} = useI18n()
const {allMembers, groupMembers, tagMembers, loadMembers} = useAnswerMembers(computed(() => props.fields))
const missing = computed(() => missingAnswers(props.fields, answers.value))

function set(fieldId: number, value: string) {
  answers.value = {...answers.value, [fieldId]: value}
}

onMounted(loadMembers)
</script>

<template>
  <div class="space-y-3" data-testid="course-questions">
    <SubHeader>{{ t('courses.questions') }}</SubHeader>
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
          :data-testid="`course-question-${field.id}`"
          @update:model-value="value => set(field.id, value)"
      />
    </div>
    <p v-if="missing.length > 0" class="text-xs text-error">
      {{ t('events.registrationFields.missing', {fields: missing.map(field => field.name).join(', ')}) }}
    </p>
  </div>
</template>
