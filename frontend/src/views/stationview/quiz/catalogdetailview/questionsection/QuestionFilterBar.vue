/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type { QuizCategory } from '@/api/quiz'

const type = defineModel<string>('type', {required: true})
const category = defineModel<string>('category', {required: true})

defineProps<{
  categories: QuizCategory[]
  typeOptions: { value: string; label: string }[]
  filteredCount: number
  totalCount: number
}>()

const { t } = useI18n()
</script>

<template>
  <div class="grid grid-cols-2 sm:flex items-center gap-2 mb-3">
    <SelectInput v-model="type" class="w-auto text-sm">
      <option value="">{{ t('quiz.questions.allTypes') }}</option>
      <option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </SelectInput>
    <SelectInput v-model="category" class="w-auto text-sm">
      <option value="">{{ t('quiz.questions.allCategories') }}</option>
      <option value="none">{{ t('quiz.questions.noCategory') }}</option>
      <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
    </SelectInput>
    <MutedText size="sm">{{ filteredCount }}/{{ totalCount }}</MutedText>
  </div>
</template>
