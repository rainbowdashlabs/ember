/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { WaitingListField } from '@/api/types'
import CustomFieldInput from './CustomFieldInput.vue'

const props = defineProps<{
  fields: WaitingListField[]
  values: Record<number, string>
  parseConfig: (configStr: string | undefined | null) => Record<string, unknown>
}>()

const emit = defineEmits<{
  (e: 'update', fieldId: number, value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <template v-if="fields.length > 0">
    <SubHeader>{{ t('waitingList.customFields') }}</SubHeader>
    <div v-for="field in fields" :key="field.id" class="space-y-1">
      <FieldLabel>{{ field.name }}{{ field.required ? ' *' : '' }}</FieldLabel>
      <CustomFieldInput
        :field="field"
        :value="values[field.id] ?? ''"
        :parse-config="parseConfig"
        @update="(v) => emit('update', field.id, v)"
      />
    </div>
  </template>
</template>
