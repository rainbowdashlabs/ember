/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import QuestionEditor from './QuestionEditor.vue'
import AddQuestionMenu from './AddQuestionMenu.vue'
import type {QuestionDraft} from './types'
import type {QuestionType} from '@/api/forms'

/** The questions of a form, in order, and the kinds of question that may be added to them. */
defineProps<{
  questions: QuestionDraft[]
  questionTypes: QuestionType[]
}>()

const emit = defineEmits<{
  move: [index: number, direction: -1 | 1]
  remove: [index: number]
  add: [type: QuestionType]
}>()
</script>

<template>
  <div class="space-y-6">
    <div class="space-y-3">
      <QuestionEditor v-for="(q, idx) in questions" :key="q.id"
          :question="q" :index="idx" :total-questions="questions.length"
          @move="(index, direction) => emit('move', index, direction)" @remove="emit('remove', $event)"/>
    </div>

    <AddQuestionMenu :question-types="questionTypes" @add="emit('add', $event)"/>
  </div>
</template>
