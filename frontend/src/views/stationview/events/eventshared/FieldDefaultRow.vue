/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldAnswerInput from '@/components/input/FieldAnswerInput.vue'
import type {AttendanceTemplateField} from '@/api/generated/schema'
import {fieldTypeLabel} from '@/api/fieldTypes'
import {DEFAULT_SOURCES} from './fieldDefaults'

/**
 * What one field of the attendance sheet is filled in with before anybody is there.
 *
 * <p>The one row of its kind. Both the editor and the quick dialog show this, and each carried its
 * own copy with the names of the appointment's own properties written into it in German, so a
 * property renamed in one place kept its old name in the other and neither could be translated.
 */
const {t} = useI18n()

const props = defineProps<{
  field: AttendanceTemplateField
  source: string
  value: string
}>()

/**
 * The choices the sheet field has, which its value is picked from, so nothing can be filled in that
 * the sheet would then refuse.
 */
const options = computed<string[]>(() => props.field.config?.options ?? [])

const emit = defineEmits<{
  updateSource: [source: string]
  updateValue: [value: string]
}>()
</script>

<template>
  <div data-testid="field-default-row" class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20 space-y-2">
    <div class="text-sm font-medium">
      {{ field.name }} <span class="text-xs text-(--text-muted)">({{ fieldTypeLabel(t, field.fieldType) }})</span>
    </div>
    <div class="grid gap-2 sm:grid-cols-2">
      <SelectInput
          :model-value="source"
          @update:model-value="emit('updateSource', ($event as string) ?? '')"
      >
        <option value="">{{ t('events.noDefault') }}</option>
        <option value="VALUE">{{ t('events.staticValue') }}</option>
        <option v-for="src in DEFAULT_SOURCES" :key="src" :value="src">
          {{ t(`events.defaultSources.${src}`) }}
        </option>
      </SelectInput>
      <FieldAnswerInput
          v-if="source === 'VALUE'"
          :field-type="field.fieldType"
          :model-value="value"
          :options="options"
          :placeholder="t('events.defaultValuePlaceholder')"
          @update:model-value="emit('updateValue', $event)"
      />
    </div>
  </div>
</template>
