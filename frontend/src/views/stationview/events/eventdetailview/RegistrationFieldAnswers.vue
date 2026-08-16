/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {EventFieldTypes, type EventRegistrationField, type RegistrationFieldValue} from '@/api/events'

const props = defineProps<{
  fields: EventRegistrationField[]
  values?: RegistrationFieldValue[]
  /** Resolves member ids for member-typed answers; ids are shown as-is without it. */
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

function displayValue(field: EventRegistrationField, raw: string): string {
  if (field.fieldType === EventFieldTypes.BOOLEAN) {
    return raw === 'true' ? t('common.yes') : t('common.no')
  }
  if (field.fieldType.startsWith('MEMBER')) {
    return raw
        .replace(/[[\]"]/g, '')
        .split(',')
        .map(part => part.trim())
        .filter(part => part !== '')
        .map(part => props.memberNames?.get(Number(part)) ?? `#${part}`)
        .join(', ')
  }
  return raw
}

const answers = computed<Answer[]>(() => {
  const byField = new Map((props.values ?? []).map(v => [v.fieldId, v.value]))
  return props.fields
      .filter(field => !props.overviewOnly || field.overview)
      .map((field) => {
        const raw = byField.get(field.id) ?? ''
        return {
          id: field.id,
          label: field.name,
          value: raw === '' ? '' : displayValue(field, raw),
          missing: raw === '' && (field.config?.required ?? false),
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
