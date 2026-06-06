/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

interface Category {
  id: number
  name: string
}

const props = defineProps<{
  categories: Category[]
  generating: boolean
  error: string
}>()

const emit = defineEmits<{
  generate: [entries: GenEntry[], userPrompt: string]
}>()

export interface GenEntry {
  quizQuestionType: string
  count: number
  categoryId: number | null
}

const { t } = useI18n()

const genEntries = ref<GenEntry[]>([{ quizQuestionType: 'MULTIPLE_CHOICE', count: 5, categoryId: null }])
const genUserPrompt = ref('')
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('quiz.ai.generateQuestions') }}</SectionHeader>

    <div class="space-y-3">
      <div v-for="(entry, eIdx) in genEntries" :key="eIdx" class="flex flex-col sm:flex-row gap-2 items-start sm:items-end p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
        <div class="flex-1">
          <FieldLabel hint class="mb-1">{{ t('quiz.questions.type') }}</FieldLabel>
          <SelectInput v-model="entry.quizQuestionType">
            <option value="MULTIPLE_CHOICE">{{ t('quiz.questionTypes.MULTIPLE_CHOICE') }}</option>
            <option value="TRUE_FALSE">{{ t('quiz.questionTypes.TRUE_FALSE') }}</option>
            <option value="FREE_ANSWER">{{ t('quiz.questionTypes.FREE_ANSWER') }}</option>
            <option value="FILL_IN_THE_BLANK">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</option>
            <option value="CONNECT">{{ t('quiz.questionTypes.CONNECT') }}</option>
            <option value="ORDERING">{{ t('quiz.questionTypes.ORDERING') }}</option>
          </SelectInput>
        </div>
        <div class="w-20">
          <FieldLabel hint class="mb-1">{{ t('quiz.ai.count') }}</FieldLabel>
          <NumberInput v-model="entry.count" />
        </div>
        <div class="flex-1">
          <FieldLabel hint class="mb-1">{{ t('quiz.questions.category') }}</FieldLabel>
          <SelectInput :model-value="String(entry.categoryId ?? '')" @update:model-value="(v: string | undefined) => entry.categoryId = v ? Number(v) : null">
            <option value="">{{ t('quiz.questions.noCategory') }}</option>
            <option v-for="cat in props.categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
          </SelectInput>
        </div>
        <DeleteButton v-if="genEntries.length > 1" @click="genEntries.splice(eIdx, 1)" />
      </div>
      <SecondaryButton :icon="['fas', 'plus']" @click="genEntries.push({ quizQuestionType: 'MULTIPLE_CHOICE', count: 5, categoryId: null })">
        {{ t('quiz.ai.addEntry') }}
      </SecondaryButton>
    </div>
    <div>
      <FieldLabel hint class="mb-1">{{ t('quiz.ai.userPrompt') }}</FieldLabel>
      <TextAreaInput v-model="genUserPrompt" :placeholder="t('quiz.ai.userPromptPlaceholder')" />
    </div>
    <p v-if="props.error" class="text-xs text-error">{{ props.error }}</p>
    <div class="flex justify-end gap-3">
      <PrimaryButton :disabled="props.generating || genEntries.every(e => e.count < 1)" @click="emit('generate', genEntries, genUserPrompt)">
        <Spinner v-if="props.generating" size="sm" class="mr-1" />
        <font-awesome-icon v-else :icon="['fas', 'brain']" class="mr-1" />
        {{ props.generating ? t('quiz.ai.generating') : t('quiz.ai.generateQuestions') }}
      </PrimaryButton>
    </div>
  </div>
</template>
