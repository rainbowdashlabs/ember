/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import type {AttendanceTemplateField} from '@/api/attendance'

defineProps<{
  field: AttendanceTemplateField
  current: { source: string; value: string }
}>()

const emit = defineEmits<{
  'set-source': [value: string]
  'set-value': [value: string]
}>()

const {t} = useI18n()

const EVENT_SOURCES = [
  {value: 'EVENT_NAME', label: 'Terminname'},
  {value: 'EVENT_DESCRIPTION', label: 'Beschreibung'},
  {value: 'EVENT_START_TIME', label: 'Startzeit'},
  {value: 'EVENT_END_TIME', label: 'Endzeit'},
]
</script>

<template>
  <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
    <div class="text-sm font-medium">
      {{ field.name }}
      <span class="text-xs text-(--text-muted)">({{ field.fieldType }})</span>
    </div>
    <div class="grid gap-2 sm:grid-cols-2">
      <SelectInput
          :model-value="current.source"
          @update:model-value="emit('set-source', String($event ?? ''))"
      >
        <option value="">{{ t('events.noDefault') }}</option>
        <option value="VALUE">{{ t('events.staticValue') }}</option>
        <option v-for="src in EVENT_SOURCES" :key="src.value" :value="src.value">{{ src.label }}</option>
      </SelectInput>
      <TextInput
          v-if="current.source === 'VALUE'"
          :model-value="current.value"
          :placeholder="t('events.defaultValuePlaceholder')"
          @update:model-value="emit('set-value', $event ?? '')"
      />
    </div>
  </div>
</template>
