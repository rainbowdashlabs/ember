/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, watch } from 'vue'
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
import {QuizQuestionTypes, type QuizCategory, type QuizQuestionTypeName} from '@/api/quiz'
import McConfigEditor from './McConfigEditor.vue'
import TfConfigEditor from './TfConfigEditor.vue'
import FillBlankConfigEditor from './FillBlankConfigEditor.vue'
import FreeAnswerConfigEditor from './FreeAnswerConfigEditor.vue'
import ConnectConfigEditor from './ConnectConfigEditor.vue'
import OrderingConfigEditor from './OrderingConfigEditor.vue'
import ImageTextConfigEditor from './ImageTextConfigEditor.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()

const title = defineModel<string>('title', {required: true})
const description = defineModel<string>('description', {required: true})
const questionType = defineModel<QuizQuestionTypeName>('questionType', {required: true})
const categoryId = defineModel<number | null>('categoryId', {required: true})
const points = defineModel<number>('points', {required: true})
const autoPoints = defineModel<boolean>('autoPoints', {required: true})
const config = defineModel<Record<string, unknown>>('config', {required: true})

const props = defineProps<{
  imagePreview: string | null
  authImageSrc?: string | null
  hasImage: boolean
  categories: QuizCategory[]
  isEditing: boolean
}>()

const emit = defineEmits<{
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
  get: () => categoryId.value === null ? '' : String(categoryId.value),
  set: (v: string | undefined) => { categoryId.value = v ? Number(v) : null },
})

const calculatedPoints = computed(() => {
  const cfg = config.value
  const type = questionType.value
  switch (type) {
    case QuizQuestionTypes.MULTIPLE_CHOICE: {
      const opts = (cfg.options as { text: string; correct: boolean }[]) || []
      const correctCount = opts.filter(o => o.correct).length
      const ppc = (cfg.pointsPerCorrect as number) || 1
      return correctCount * ppc
    }
    case QuizQuestionTypes.FILL_IN_THE_BLANK: {
      const answers = (cfg.answers as string[]) || []
      const ppcFill = (cfg.pointsPerCorrect as number) || 1
      return (answers.length || 1) * ppcFill
    }
    case QuizQuestionTypes.FREE_ANSWER: {
      const answers = (cfg.answers as string[]) || []
      const ppcFree = (cfg.pointsPerCorrect as number) || 1
      return (answers.length || 1) * ppcFree
    }
    case QuizQuestionTypes.CONNECT: {
      const pairs = (cfg.pairs as { left: string; right: string }[]) || []
      const ppcConn = (cfg.pointsPerCorrect as number) || 1
      return (pairs.length || 1) * ppcConn
    }
    case QuizQuestionTypes.ORDERING: {
      const items = (cfg.items as string[]) || []
      const ppcOrd = (cfg.pointsPerCorrect as number) || 1
      return (items.length || 1) * ppcOrd
    }
    case QuizQuestionTypes.TRUE_FALSE:
      return 1
    case QuizQuestionTypes.IMAGE_TEXT:
      return 1
    default:
      return 1
  }
})

watch(calculatedPoints, (val) => {
  if (autoPoints.value) {
    points.value = val
  }
})

function onTypeChange(val: string | number | null | undefined) {
  if (!val) return
  questionType.value = String(val) as QuizQuestionTypeName
}
</script>

<template>
  <div class="space-y-3">
    <TextInput :model-value="title" :placeholder="t('quiz.questions.questionTitle')" @update:model-value="(v: string | undefined) => title = v ?? ''" />
    <TextAreaInput :model-value="description" :placeholder="t('quiz.questions.description')" @update:model-value="(v: string | undefined) => description = v ?? ''" />
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.questions.type') }}</FieldLabel>
        <SelectInput :model-value="questionType" :disabled="isEditing" class="w-full" @update:model-value="onTypeChange">
          <option v-for="qt in allQuestionTypes" :key="qt" :value="qt">{{ t(`quiz.questionTypes.${qt}`) }}</option>
        </SelectInput>
      </div>
      <div>
        <FieldLabel hint class="mb-1">{{ t('quiz.questions.category') }}</FieldLabel>
        <SelectInput v-model="categoryIdStr" class="w-full">
          <option value="">{{ t('quiz.questions.noCategory') }}</option>
          <option v-for="cat in categories" :key="cat.id" :value="String(cat.id)">{{ cat.name }}</option>
        </SelectInput>
      </div>
    </div>
    <div class="flex items-center gap-4 flex-wrap">
      <FieldLabel inline>
        <ToggleInput v-model="autoPoints" />
        {{ t('quiz.questions.autoPoints') }}
      </FieldLabel>
      <span v-if="autoPoints" class="text-sm text-(--text-muted)">= {{ calculatedPoints }} {{ t('quiz.points') }}</span>
      <div v-else class="flex items-center gap-2">
        <FieldHint>{{ t('quiz.questions.points') }}</FieldHint>
        <NumberInput :model-value="points" class="w-20" @update:model-value="(v: number | undefined) => points = v ?? 1" />
      </div>
    </div>

    <!-- Image upload (only for IMAGE_TEXT) -->
    <ImageUploadField v-if="questionType === QuizQuestionTypes.IMAGE_TEXT" :image-preview="imagePreview" :auth-src="authImageSrc" @select-image="(e: Event) => emit('selectImage', e)" @remove-image="emit('removeImage')" />

    <!-- Type-specific config editors -->
    <McConfigEditor v-if="questionType === QuizQuestionTypes.MULTIPLE_CHOICE" v-model:config="config" :question-title="title" />
    <FillBlankConfigEditor v-if="questionType === QuizQuestionTypes.FILL_IN_THE_BLANK" v-model:config="config" />
    <FreeAnswerConfigEditor v-if="questionType === QuizQuestionTypes.FREE_ANSWER || questionType === QuizQuestionTypes.ENUMERATION" v-model:config="config" />
    <ConnectConfigEditor v-if="questionType === QuizQuestionTypes.CONNECT" v-model:config="config" />
    <ImageTextConfigEditor v-if="questionType === QuizQuestionTypes.IMAGE_TEXT" v-model:config="config" />
    <TfConfigEditor v-if="questionType === QuizQuestionTypes.TRUE_FALSE" v-model:config="config" />
    <OrderingConfigEditor v-if="questionType === QuizQuestionTypes.ORDERING" v-model:config="config" />

    <!-- Save / Cancel -->
    <div class="flex justify-end gap-3 pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :disabled="!title.trim()" @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
    </div>
  </div>
</template>
