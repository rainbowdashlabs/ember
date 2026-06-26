/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  config: Record<string, unknown>
  disabled: boolean
  fillGaps: Record<string, string>
}>()

const emit = defineEmits<{
  setFillGap: [gapIndex: number, value: string]
}>()

const { t } = useI18n()

const fillAnswers = computed<string[]>(() => (props.config.answers as string[]) ?? [])
const fillGapCount = computed(() => (props.config.gapCount as number) ?? fillAnswers.value.length)
</script>

<template>
  <p v-if="config.text" class="text-sm whitespace-pre-wrap">{{ config.text }}</p>
  <div class="space-y-2">
    <div
      v-for="gapIdx in fillGapCount"
      :key="gapIdx - 1"
      class="flex items-center gap-2"
    >
      <span class="text-sm text-(--text-muted) w-6">{{ gapIdx }}.</span>
      <input
        :value="fillGaps[String(gapIdx - 1)] ?? ''"
        :disabled="disabled"
        class="flex-1 px-3 py-2 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary text-sm disabled:opacity-60"
        :placeholder="t('quiz.attempt.fillGapPlaceholder', { n: gapIdx })"
        @input="emit('setFillGap', gapIdx - 1, ($event.target as HTMLInputElement).value)"
      />
    </div>
  </div>
</template>
