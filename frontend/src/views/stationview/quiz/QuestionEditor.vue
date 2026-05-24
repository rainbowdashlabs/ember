/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import ImageUploadField from './ImageUploadField.vue'
import type { QuizCategory, QuizQuestionTypeName } from '@/api/types'
import { QuizQuestionTypes } from '@/api/types'
import McConfigEditor from './McConfigEditor.vue'
import TfConfigEditor from './TfConfigEditor.vue'
import FillBlankConfigEditor from './FillBlankConfigEditor.vue'
import FreeAnswerConfigEditor from './FreeAnswerConfigEditor.vue'
import ConnectConfigEditor from './ConnectConfigEditor.vue'
import OrderingConfigEditor from './OrderingConfigEditor.vue'
import ImageTextConfigEditor from './ImageTextConfigEditor.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()

const props = defineProps<{
  title: string
  description: string
  questionType: QuizQuestionTypeName
  categoryId: number | null
  points: number
  autoPoints: boolean
  imagePreview: string | null
  hasImage: boolean
  config: Record<string, unknown>
  categories: QuizCategory[]
  isEditing: boolean
}>()

const emit = defineEmits<{
  'update:title': [value: string]
  'update:description': [value: string]
  'update:questionType': [value: QuizQuestionTypeName]
  'update:categoryId': [value: number | null]
  'update:points': [value: number]
  'update:autoPoints': [value: boolean]
  'update:config': [value: Record<string, unknown>]
  save: []
  cancel: []
  selectImage: [event: Event]
  removeImage: []
}>()

const allQuestionTypes: QuizQuestionTypeName[] = [
  QuizQuestionTypes.MULTIPLE_CHOICE,
  QuizQuestionTypes.FILL_IN_THE_BLANK,
  QuizQuestionTypes.FREE_ANSWER,
  QuizQuestionTypes.CONNECT,
  QuizQuestionTypes.IMAGE_TEXT,
  QuizQuestionTypes.TRUE_FALSE,
  QuizQuestionTypes.ORDERING,
]

const categoryIdStr = computed({
  get: () => props.categoryId === null ? '' : String(props.categoryId),
  set: (v: string | undefined) => emit('update:categoryId', v ? Number(v) : null),
})

const calculatedPoints = computed(() => {
  const config = props.config
  const type = props.questionType
  switch (type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const opts = (config.options as { text: string; correct: boolean }[]) || []
      const correctCount = opts.filter(o => o.correct).length
      const ppc = (config.pointsPerCorrect as number) || 0.5
      return correctCount * ppc
    }
    case QuizQuestionTypes.FILL_IN_THE_BLANK: {
      const answers = (config.answers as string[]) || []
      return answers.length || 1
    }
    case QuizQuestionTypes.FREE_ANSWER: {
      const answers = (config.answers as string[]) || []
      return answers.length || 1
    }
    case QuizQuestionTypes.CONNECT: {
      const pairs = (config.pairs as { left: string; right: string }[]) || []
      return pairs.length || 1
    }
    case QuizQuestionTypes.ORDERING: {
      const items = (config.items as string[]) || []
      return items.length || 1
    }
    case QuizQuestionTypes.TRUE_FALSE:
      return 1
    case QuizQuestionTypes.IMAGE_TEXT:
      return 1
    default:
      return 1
  }
})

function onTypeChange(val: QuizQuestionTypeName | string | undefined) {
  if (!val) return
  emit('update:questionType', val as QuizQuestionTypeName)
}
</script>

<template>
  <div class="space-y-3">
    <TextInput :model-value="title" :placeholder="t('quiz.questions.questionTitle')" @update:model-value="(v: string | undefined) => emit('update:title', v ?? '')" />
    <TextAreaInput :model-value="description" :placeholder="t('quiz.questions.description')" @update:model-value="(v: string | undefined) => emit('update:description', v ?? '')" />
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.questions.type') }}</FieldLabel>
        <SelectInput :model-value="questionType" :disabled="isEditing" @update:model-value="onTypeChange">
          <option v-for="qt in allQuestionTypes" :key="qt" :value="qt">{{ t(`quiz.questionTypes.${qt}`) }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.questions.category') }}</FieldLabel>
        <SelectInput v-model="categoryIdStr">
          <option value="">{{ t('quiz.questions.noCategory') }}</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>
    </div>
    <div class="flex items-center gap-4 flex-wrap">
      <FieldLabel inline>
        <ToggleInput :model-value="autoPoints" @update:model-value="(v: boolean) => emit('update:autoPoints', v)" />
        {{ t('quiz.questions.autoPoints') }}
      </FieldLabel>
      <span v-if="autoPoints" class="text-sm text-(--text-muted)">= {{ calculatedPoints }} {{ t('quiz.points') }}</span>
      <div v-else class="flex items-center gap-2">
        <FieldHint>{{ t('quiz.questions.points') }}</FieldHint>
        <NumberInput :model-value="points" class="w-20" @update:model-value="(v: number | undefined) => emit('update:points', v ?? 1)" />
      </div>
    </div>

    <!-- Image upload (only for IMAGE_TEXT) -->
    <ImageUploadField v-if="questionType === QuizQuestionTypes.IMAGE_TEXT" :image-preview="imagePreview" @select-image="(e: Event) => emit('selectImage', e)" @remove-image="emit('removeImage')" />

    <!-- Type-specific config editors -->
    <McConfigEditor v-if="questionType === QuizQuestionTypes.MULTIPLE_CHOICE" :config="config" :question-title="title" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <FillBlankConfigEditor v-if="questionType === QuizQuestionTypes.FILL_IN_THE_BLANK" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <FreeAnswerConfigEditor v-if="questionType === QuizQuestionTypes.FREE_ANSWER" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <ConnectConfigEditor v-if="questionType === QuizQuestionTypes.CONNECT" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <ImageTextConfigEditor v-if="questionType === QuizQuestionTypes.IMAGE_TEXT" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <TfConfigEditor v-if="questionType === QuizQuestionTypes.TRUE_FALSE" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />
    <OrderingConfigEditor v-if="questionType === QuizQuestionTypes.ORDERING" :config="config" @update:config="(v: Record<string, unknown>) => emit('update:config', v)" />

    <!-- Save / Cancel -->
    <div class="flex justify-end gap-3 pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="!title.trim()" @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
    </div>
  </div>
</template>
