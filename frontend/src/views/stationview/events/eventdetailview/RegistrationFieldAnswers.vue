/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {EventRegistrationFieldValue, EventRegistrationField} from '@/api/generated/schema'
import {answerText} from '@/util/questions'

const props = defineProps<{
  fields: EventRegistrationField[]
  values?: EventRegistrationFieldValue[]
  /** Resolves member ids for member-typed answers; ids are shown as numbers without it. */
  memberNames?: Map<number, string>
  /** Only the questions marked for the list, which is what a row shows. */
  overviewOnly?: boolean
}>()

const {t} = useI18n()

interface Answer {
  id: number
  label: string
  value: string
  missing: boolean
}

const answers = computed<Answer[]>(() => {
  const byField = new Map((props.values ?? []).map(v => [v.fieldId, v.value]))
  const words = {yes: t('common.yes'), no: t('common.no'), names: props.memberNames}
  return props.fields
      .filter(field => !props.overviewOnly || field.overview)
      .map((field) => {
        const raw = byField.get(field.id) ?? ''
        return {
          id: field.id,
          label: field.name,
          value: answerText(field.fieldType, raw, words),
          missing: raw === '' && field.config.required,
        }
      })
      .filter(answer => answer.value !== '' || answer.missing)
})
</script>

<template>
  <div v-if="answers.length > 0" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
    <span v-for="answer in answers" :key="answer.id" class="text-(--text-muted)">
      {{ answer.label }}:
      <span v-if="answer.missing" class="text-error">{{ t('events.registrationFields.missingShort') }}</span>
      <span v-else class="text-(--text) font-medium">{{ answer.value }}</span>
    </span>
  </div>
</template>
