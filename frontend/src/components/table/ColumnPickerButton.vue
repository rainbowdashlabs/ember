/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { ColumnPickerOption } from './columns'

const { t } = useI18n()

defineProps<{
  options: ColumnPickerOption[]
  emptyLabel?: string
  fullWidth?: boolean
}>()

const emit = defineEmits<{
  toggle: [key: string | number]
}>()

const open = ref(false)
</script>

<template>
  <div class="relative">
    <SecondaryButton :icon="['fas', 'table-columns']" :full-width="fullWidth" @click="open = !open">
      {{ t('tableFilter.columns') }}
    </SecondaryButton>
    <div v-if="open" class="absolute right-0 top-full mt-1 z-10 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-3 min-w-48 space-y-1">
      <p class="text-xs font-semibold text-(--text-muted) mb-2">{{ t('tableFilter.columns') }}</p>
      <div v-if="options.length === 0 && emptyLabel" class="text-xs text-(--text-muted)">{{ emptyLabel }}</div>
      <FieldLabel v-for="option in options" :key="option.key" inline class="cursor-pointer py-0.5">
        <CheckboxInput :model-value="option.visible" @update:model-value="emit('toggle', option.key)" />
        {{ option.label }}
      </FieldLabel>
    </div>
  </div>
</template>
