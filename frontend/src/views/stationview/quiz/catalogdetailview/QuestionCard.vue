/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import QuestionCardCollapsedMobile from './QuestionCardCollapsedMobile.vue'
import QuestionCardCollapsedDesktop from './QuestionCardCollapsedDesktop.vue'
import QuestionInlineEditor from './QuestionInlineEditor.vue'
import type { QuizCategory, QuizQuestion, QuizQuestionTypeName } from '@/api/quiz'
import { useBreakpoint } from '@/composables/useBreakpoint'

const editorTitle = defineModel<string>('editorTitle', {required: true})
const editorDescription = defineModel<string>('editorDescription', {required: true})
const editorQuestionType = defineModel<QuizQuestionTypeName>('editorQuestionType', {required: true})
const editorCategoryId = defineModel<number | null>('editorCategoryId', {required: true})
const editorPoints = defineModel<number>('editorPoints', {required: true})
const editorAutoPoints = defineModel<boolean>('editorAutoPoints', {required: true})
const editorConfig = defineModel<Record<string, unknown>>('editorConfig', {required: true})

defineProps<{
  question: QuizQuestion
  expanded: boolean
  selected: boolean
  categoryName: string
  categories: QuizCategory[]
  editorImagePreview: string | null
  editorAuthImageSrc: string | null
  editorHasImage: boolean
}>()

const emit = defineEmits<{
  toggleSelect: []
  edit: []
  delete: []
  'select-image': [event: Event]
  'remove-image': []
  save: []
  cancel: []
}>()

const { isMobile } = useBreakpoint()
</script>

<template>
  <NeutralContainer>
    <template v-if="!expanded">
      <QuestionCardCollapsedMobile
        v-if="isMobile"
        :question="question"
        :selected="selected"
        :category-name="categoryName"
        @toggle-select="emit('toggleSelect')"
        @edit="emit('edit')"
        @delete="emit('delete')"
      />
      <QuestionCardCollapsedDesktop
        v-else
        :question="question"
        :selected="selected"
        :category-name="categoryName"
        @toggle-select="emit('toggleSelect')"
        @edit="emit('edit')"
        @delete="emit('delete')"
      />
    </template>
    <QuestionInlineEditor
      v-else
      :is-editing="true"
      v-model:title="editorTitle"
      v-model:description="editorDescription"
      v-model:question-type="editorQuestionType"
      v-model:category-id="editorCategoryId"
      v-model:points="editorPoints"
      v-model:auto-points="editorAutoPoints"
      v-model:config="editorConfig"
      :image-preview="editorImagePreview"
      :auth-image-src="editorAuthImageSrc"
      :has-image="editorHasImage"
      :categories="categories"
      @select-image="emit('select-image', $event)"
      @remove-image="emit('remove-image')"
      @save="emit('save')"
      @cancel="emit('cancel')"
    />
  </NeutralContainer>
</template>
