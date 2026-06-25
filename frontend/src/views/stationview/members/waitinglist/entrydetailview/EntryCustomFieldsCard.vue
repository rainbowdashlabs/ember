/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EntryCustomFieldInput from './EntryCustomFieldInput.vue'
import type { WaitingListField } from '@/api/types'

defineProps<{
  fields: WaitingListField[]
  values: Map<number, string>
}>()

const emit = defineEmits<{
  'update:value': [fieldId: number, value: string]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer v-if="fields.length > 0" class="space-y-4">
    <SubHeader>{{ t('waitingList.customFields') }}</SubHeader>
    <div class="grid gap-4 sm:grid-cols-2">
      <EntryCustomFieldInput
        v-for="field in fields"
        :key="field.id"
        :field="field"
        :value="values.get(field.id) ?? ''"
        @update:value="(v: string) => emit('update:value', field.id, v)"
      />
    </div>
  </NeutralContainer>
</template>
