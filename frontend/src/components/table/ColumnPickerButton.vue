/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, type CSSProperties } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Popover from '@/components/feedback/Popover.vue'
import type { ColumnPickerOption } from './columns'

/**
 * The list of columns a table can show, each ticked on or off, with a choice for all and none.
 *
 * <p>It stays open while the reader ticks through it and closes on a press anywhere else, on
 * escape, or when focus is tabbed out of it.
 *
 * <p>A list taller than the screen is laid out in as many columns as it takes to fit, rather than
 * scrolled: a table with forty questions would otherwise hide most of them below the fold of a
 * panel that is itself below the button.
 *
 * <p>All and none are one change, sent as `setVisible` with the columns whose state it changes, so
 * a screen that stores the choice elsewhere writes it once rather than once per column.
 */
const props = defineProps<{
  options: ColumnPickerOption[]
  emptyLabel?: string
  fullWidth?: boolean
}>()

const emit = defineEmits<{
  toggle: [key: string | number]
  setVisible: [keys: (string | number)[], visible: boolean]
}>()

const { t } = useI18n()

/** The height of one ticked line, and what the heading and the two buttons take above the list. */
const LINE_HEIGHT = 28
const CHROME_HEIGHT = 96
const SCREEN_SHARE = 0.6

const screenHeight = ref(typeof window === 'undefined' ? 800 : window.innerHeight)

function measure() {
  screenHeight.value = window.innerHeight
}

onMounted(() => window.addEventListener('resize', measure))
onBeforeUnmount(() => window.removeEventListener('resize', measure))

const linesPerColumn = computed(() =>
  Math.max(4, Math.floor((screenHeight.value * SCREEN_SHARE - CHROME_HEIGHT) / LINE_HEIGHT)))

const listStyle = computed<CSSProperties>(() => {
  const columns = Math.max(1, Math.ceil(props.options.length / linesPerColumn.value))
  const rows = Math.ceil(props.options.length / columns)
  return { gridTemplateRows: `repeat(${rows}, auto)` }
})

function setAll(visible: boolean) {
  const changing = props.options.filter(option => option.visible !== visible).map(option => option.key)
  if (changing.length > 0) emit('setVisible', changing, visible)
}
</script>

<template>
  <Popover
      :class="fullWidth ? 'block' : 'inline-block'"
      :label="t('tableFilter.columns')"
      panel-class="p-3 min-w-48 max-w-[calc(100vw-8px)] overflow-x-auto space-y-2"
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
    <div class="flex items-center justify-between gap-3">
      <p class="text-xs font-semibold text-(--text-muted)">{{ t('tableFilter.columns') }}</p>
      <div v-if="options.length > 1" class="flex gap-1">
        <SecondaryButton compact data-testid="column-picker-all" @click="setAll(true)">{{ t('tableFilter.selectAll') }}</SecondaryButton>
        <SecondaryButton compact data-testid="column-picker-none" @click="setAll(false)">{{ t('tableFilter.selectNone') }}</SecondaryButton>
      </div>
    </div>
    <div v-if="options.length === 0 && emptyLabel" class="text-xs text-(--text-muted)">{{ emptyLabel }}</div>
    <div :style="listStyle" class="grid grid-flow-col gap-x-4">
      <FieldLabel v-for="option in options" :key="option.key" class="cursor-pointer py-0.5 whitespace-nowrap" inline>
        <CheckboxInput :model-value="option.visible" @update:model-value="emit('toggle', option.key)"/>
        {{ option.label }}
      </FieldLabel>
    </div>
  </Popover>
</template>
