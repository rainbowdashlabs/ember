/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {AttendanceTemplateField} from '@/api/types'

interface FieldDefaultEntry {
  source: string
  value: string
}

const props = defineProps<{
  fields: AttendanceTemplateField[]
  defaults: Map<number, FieldDefaultEntry>
}>()

const emit = defineEmits<{
  'update:source': [fieldId: number, source: string]
  'update:value': [fieldId: number, value: string]
}>()

const {t} = useI18n()

const EVENT_SOURCES = [
  {value: 'EVENT_NAME', label: 'Terminname'},
  {value: 'EVENT_DESCRIPTION', label: 'Beschreibung'},
  {value: 'EVENT_START_TIME', label: 'Startzeit'},
  {value: 'EVENT_END_TIME', label: 'Endzeit'},
]

function entryFor(fieldId: number): FieldDefaultEntry {
  return props.defaults.get(fieldId) ?? {source: '', value: ''}
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('events.fieldDefaults') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
    <div class="space-y-2">
      <div
          v-for="field in props.fields"
          :key="field.id"
          class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2"
      >
        <div class="text-sm font-medium">
          {{ field.name }}
          <span class="text-xs text-(--text-muted)">({{ field.fieldType }})</span>
        </div>
        <div class="grid gap-2 sm:grid-cols-2">
          <SelectInput
              :model-value="entryFor(field.id).source"
              @update:model-value="emit('update:source', field.id, String($event ?? ''))"
          >
            <option value="">{{ t('events.noDefault') }}</option>
            <option value="VALUE">{{ t('events.staticValue') }}</option>
            <option v-for="src in EVENT_SOURCES" :key="src.value" :value="src.value">{{ src.label }}</option>
          </SelectInput>
          <TextInput
              v-if="entryFor(field.id).source === 'VALUE'"
              :model-value="entryFor(field.id).value"
              :placeholder="t('events.defaultValuePlaceholder')"
              @update:model-value="emit('update:value', field.id, String($event ?? ''))"
          />
        </div>
      </div>
    </div>
  </NeutralContainer>
</template>
