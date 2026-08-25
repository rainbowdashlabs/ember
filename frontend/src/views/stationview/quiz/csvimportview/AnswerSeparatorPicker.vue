/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {ANSWER_SEPARATOR_PRESETS} from './quizCsvImport'

const separator = defineModel<string>('separator', {required: true})

defineProps<{
  label?: string
  inline?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div :class="inline ? 'flex items-center gap-1 flex-wrap' : ''">
    <FieldLabel v-if="!inline" hint class="mb-1">{{ label ?? t('quiz.csv.answerSeparator') }}</FieldLabel>
    <span v-else class="text-xs text-(--text-muted) mr-1">{{ label ?? t('quiz.csv.answerSeparator') }}:</span>
    <div :class="inline ? 'contents' : 'flex items-center gap-1'">
      <SelectionToggleButton
          v-for="preset in ANSWER_SEPARATOR_PRESETS"
          :key="preset.label"
          :selected="separator === preset.value"
          @toggle="separator = preset.value"
      >
        {{ preset.label }}
      </SelectionToggleButton>
    </div>
  </div>
</template>
