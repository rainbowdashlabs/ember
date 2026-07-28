/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import type { QuestionType } from '@/api/forms'

defineProps<{
  questionType: QuestionType
  index: number
  totalQuestions: number
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  remove: [index: number]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between">
    <SectionLabel>
      {{ index + 1 }}. {{ t(`forms.questionTypes.${questionType}`) }}
    </SectionLabel>
    <div class="flex gap-1">
      <MutedIconButton :icon="['fas', 'chevron-up']" label="Up" :disabled="index === 0"
                       @click="emit('move', index, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" label="Down" :disabled="index === totalQuestions - 1"
                       @click="emit('move', index, 1)" />
      <DeleteButton @click="emit('remove', index)" />
    </div>
  </div>
</template>
