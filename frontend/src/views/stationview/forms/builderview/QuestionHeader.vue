/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import type { QuestionType } from '@/api/forms'

/** The top line of a question's tile: its number, its kind, and in the slot the menu in its corner. */
defineProps<{
  questionType: QuestionType
  index: number
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between">
    <SectionLabel>
      {{ index + 1 }}. {{ t(`forms.questionTypes.${questionType}`) }}
    </SectionLabel>
    <slot/>
  </div>
</template>
