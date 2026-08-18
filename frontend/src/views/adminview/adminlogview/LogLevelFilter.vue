/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {LOG_LEVELS} from '@/api/applicationLog'

/**
 * Which severities are shown.
 *
 * Toggles rather than a select, because the useful question is almost never "only warnings" but
 * "warnings and errors and nothing else", and that is one glance rather than a menu.
 */
const levels = defineModel<string[]>({required: true})

const emit = defineEmits<{
  change: []
}>()

const {t} = useI18n()

function toggle(level: string) {
  levels.value = levels.value.includes(level)
      ? levels.value.filter(entry => entry !== level)
      : [...levels.value, level]
  emit('change')
}
</script>

<template>
  <div class="flex gap-4 flex-wrap items-center">
    <FieldLabel>{{ t('applicationLog.levels') }}</FieldLabel>
    <label v-for="level in LOG_LEVELS" :key="level" class="flex items-center gap-2 text-sm">
      <ToggleInput
          :model-value="levels.includes(level)"
          :aria-label="level"
          @update:model-value="toggle(level)"/>
      {{ level }}
    </label>
  </div>
</template>
