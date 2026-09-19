/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Popover from '@/components/feedback/Popover.vue'
import type { ColumnPickerOption } from './columns'

/**
 * The list of columns a table can show, each ticked on or off.
 *
 * <p>It stays open while the reader ticks through it and closes on a press anywhere else, on
 * escape, or when focus is tabbed out of it.
 */
defineProps<{
  options: ColumnPickerOption[]
  emptyLabel?: string
  fullWidth?: boolean
}>()

const emit = defineEmits<{
  toggle: [key: string | number]
}>()

const { t } = useI18n()
</script>

<template>
  <Popover
      :class="fullWidth ? 'block' : 'inline-block'"
      :label="t('tableFilter.columns')"
      panel-class="p-3 min-w-48 space-y-1"
      role="dialog"
      test-id="column-picker"
  >
    <template #trigger="{toggle, triggerAttrs}">
      <SecondaryButton
          :full-width="fullWidth"
          :icon="['fas', 'table-columns']"
          data-testid="column-picker-trigger"
          v-bind="triggerAttrs"
          @click="toggle"
      >
        {{ t('tableFilter.columns') }}
      </SecondaryButton>
    </template>
    <p class="text-xs font-semibold text-(--text-muted) mb-2">{{ t('tableFilter.columns') }}</p>
    <div v-if="options.length === 0 && emptyLabel" class="text-xs text-(--text-muted)">{{ emptyLabel }}</div>
    <FieldLabel v-for="option in options" :key="option.key" class="cursor-pointer py-0.5" inline>
      <CheckboxInput :model-value="option.visible" @update:model-value="emit('toggle', option.key)"/>
      {{ option.label }}
    </FieldLabel>
  </Popover>
</template>
