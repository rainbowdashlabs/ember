/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FormPageIntro from './FormPageIntro.vue'
import QuestionHeading from './QuestionHeading.vue'
import PublicQuestionFields from './PublicQuestionFields.vue'
import type {FormAnswerValue, PublicFormPage, PublicFormQuestion} from '@/api/generated/schema'
import type {useFormWalk} from '@/composables/useFormWalk'

/**
 * The page of a public form the reader is on: how far along they are, and its questions.
 *
 * <p>Shared by the form's own page, where each question sits on a card of its own, and the form set
 * into a public page among other things, where a card would be a box in a box.
 */
defineProps<{
  walk: ReturnType<typeof useFormWalk<PublicFormPage, PublicFormQuestion>>
  answers: Record<number, FormAnswerValue>
  /** Whether each question stands on a card of its own. */
  framed?: boolean
}>()

const emit = defineEmits<{
  'update-text': [question: PublicFormQuestion, text: string]
  'update-date': [question: PublicFormQuestion, date: string]
  'toggle-choice': [question: PublicFormQuestion, optionKey: string]
}>()
</script>

<template>
  <FormPageIntro v-if="walk.paged.value" :page-number="walk.pageNumber.value" :progress="walk.progress.value"
                 :title="walk.currentPage.value?.title" :description="walk.currentPage.value?.description"/>

  <div :class="framed ? 'space-y-4' : 'space-y-3'">
    <component :is="framed ? NeutralContainer : 'div'" v-for="q in walk.currentQuestions.value" :key="q.id"
               :class="walk.errors.value[q.id] ? 'ring-2 ring-error rounded-theme' : ''">
      <div class="space-y-2">
        <QuestionHeading :title="q.title" :description="q.description" :required="q.required"
                         :error="walk.errors.value[q.id]" :compact="!framed"/>
        <PublicQuestionFields
            :question="q"
            :answer="answers[q.id]"
            @update:text="(v: string) => emit('update-text', q, v)"
            @update:date="(v: string) => emit('update-date', q, v)"
            @toggle-choice="(key: string) => emit('toggle-choice', q, key)"/>
      </div>
    </component>
  </div>
</template>
