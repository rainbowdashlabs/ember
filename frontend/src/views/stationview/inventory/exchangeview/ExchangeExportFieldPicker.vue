/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type { ProfileField } from '@/api/types'

const { t } = useI18n()

defineProps<{
  fields: ProfileField[]
  selectedFieldIds: Set<number>
}>()

const emit = defineEmits<{
  toggleField: [fieldId: number]
}>()
</script>

<template>
  <NeutralContainer v-if="fields.length > 0" class="space-y-2">
    <p class="text-sm font-medium">{{ t('exchanges.exportFieldsHint') }}</p>
    <div class="flex flex-wrap gap-2">
      <label v-for="field in fields" :key="field.id" class="inline-flex items-center gap-1.5 text-sm cursor-pointer">
        <CheckboxInput :model-value="selectedFieldIds.has(field.id)" @update:model-value="emit('toggleField', field.id)" />
        {{ field.name }}
      </label>
    </div>
  </NeutralContainer>
</template>
