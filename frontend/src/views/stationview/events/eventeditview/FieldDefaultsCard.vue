/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldDefaultRow from '../eventshared/FieldDefaultRow.vue'
import type {AttendanceTemplateField} from '@/api/attendance'

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

function entryFor(fieldId: number): FieldDefaultEntry {
  return props.defaults.get(fieldId) ?? {source: '', value: ''}
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('events.fieldDefaults') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('events.fieldDefaultsHint') }}</p>
    <div class="space-y-2">
      <FieldDefaultRow
          v-for="field in props.fields"
          :key="field.id"
          :field="field"
          :source="entryFor(field.id).source"
          :value="entryFor(field.id).value"
          @update-source="emit('update:source', field.id, $event)"
          @update-value="emit('update:value', field.id, $event)"
      />
    </div>
  </NeutralContainer>
</template>
