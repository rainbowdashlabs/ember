/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import QuestionEditor from './QuestionEditor.vue'
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

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <div class="space-y-3">
      <QuestionEditor v-for="(q, idx) in questions" :key="q.id"
          :question="q" :index="idx" :total-questions="questions.length"
          @move="(index, direction) => emit('move', index, direction)" @remove="emit('remove', $event)"/>
    </div>

    <div class="flex flex-wrap gap-2">
      <SecondaryButton :icon="['fas', 'plus']" v-for="type in questionTypes" :key="type" @click="emit('add', type)">
        {{ t(`forms.questionTypes.${type}`) }}
      </SecondaryButton>
    </div>
  </div>
</template>
