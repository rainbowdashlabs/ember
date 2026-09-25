/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PublicQuestionFields from '@/components/forms/fill/PublicQuestionFields.vue'
import type {PublicFormQuestion} from '@/api/publicForms'

const emit = defineEmits<{
  (e: 'update:text', text: string): void
  (e: 'update:date', date: string): void
  (e: 'toggle-choice', optionIndex: number): void
}>()

defineProps<{
  question: PublicFormQuestion
  answer: Record<string, unknown>
}>()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div>
        <span class="font-medium">{{ question.title }}</span>
        <span v-if="question.required" class="ml-1 text-error">*</span>
        <MutedText v-if="question.description" tag="p" class="mt-0.5">{{ question.description }}</MutedText>
      </div>

      <PublicQuestionFields
          :question="question"
          :answer="answer"
          @update:text="(v: string) => emit('update:text', v)"
          @update:date="(v: string) => emit('update:date', v)"
          @toggle-choice="(oi: number) => emit('toggle-choice', oi)"/>
    </div>
  </NeutralContainer>
</template>
