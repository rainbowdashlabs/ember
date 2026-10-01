/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import WaitingListAnswerInput from '@/components/waitinglist/WaitingListAnswerInput.vue'
import type { WaitingListField } from '@/api/generated/schema'

defineProps<{
  fields: WaitingListField[]
  values: Record<number, string>
}>()

const emit = defineEmits<{
  (e: 'update', fieldId: number, value: string): void
}>()

const { t } = useI18n()
</script>

<template>
  <template v-if="fields.length > 0">
    <SubHeader>{{ t('waitingList.customFields') }}</SubHeader>
    <WaitingListAnswerInput
      v-for="field in fields"
      :key="field.id"
      :field="field"
      :model-value="values[field.id] ?? ''"
      @update:model-value="(v) => emit('update', field.id, v)"
    />
  </template>
</template>
