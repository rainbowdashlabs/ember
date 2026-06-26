/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import QuestionEditor from '../QuestionEditor.vue'
import type { QuizCategory, QuizQuestionTypeName } from '@/api/types'

const title = defineModel<string>('title', {required: true})
const description = defineModel<string>('description', {required: true})
const questionType = defineModel<QuizQuestionTypeName>('questionType', {required: true})
const categoryId = defineModel<number | null>('categoryId', {required: true})
const points = defineModel<number>('points', {required: true})
const autoPoints = defineModel<boolean>('autoPoints', {required: true})
const config = defineModel<Record<string, unknown>>('config', {required: true})

defineProps<{
  isEditing: boolean
  imagePreview: string | null
  authImageSrc: string | null
  hasImage: boolean
  categories: QuizCategory[]
}>()

const emit = defineEmits<{
  'select-image': [event: Event]
  'remove-image': []
  save: []
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ isEditing ? t('quiz.questions.edit') : t('quiz.questions.create') }}</SubHeader>
      <MutedIconButton :icon="['fas', 'xmark']" label="Close" hover="error" @click="emit('cancel')" />
    </div>
    <QuestionEditor
      v-model:title="title"
      v-model:description="description"
      v-model:question-type="questionType"
      v-model:category-id="categoryId"
      v-model:points="points"
      v-model:auto-points="autoPoints"
      v-model:config="config"
      :image-preview="imagePreview"
      :auth-image-src="authImageSrc"
      :has-image="hasImage"
      :categories="categories"
      :is-editing="isEditing"
      @select-image="emit('select-image', $event)"
      @remove-image="emit('remove-image')"
      @save="emit('save')"
      @cancel="emit('cancel')"
    />
  </div>
</template>
