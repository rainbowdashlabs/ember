/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DragList from '@/components/input/DragList.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {Placeholder, TemplateQuestionKind} from '@/api/generated/schema'
import {moveWithin} from '@/util/reorder'
import type {TemplateDraft} from './templateDraft'
import QuestionRow from './QuestionRow.vue'
import {ANSWER_PREFIX, QUESTION_KINDS, newQuestion} from './templateQuestions'

/**
 * What a template asks the person generating a document: a text, a date, a number, one of a few
 * entries or a tick, each within the limits the template sets. The template names
 * each answer as a placeholder of its own, in a letter's text, on a PDF's field or in a form field of
 * the PDF. A tick prints as a ticked or an empty box and ticks a check field.
 *
 * <p>A suggestion fills the answer in advance for the member chosen, and it is the answer wherever
 * nobody is asked: in self service, in a run over many members and on an appointment's copy. A check
 * whose suggestion names a yes or no question of the profile follows the member's data until the
 * person generating ticks or clears it.
 */
const draft = defineModel<TemplateDraft>({required: true})

defineProps<{
  /** What a suggestion can name: the owner's values, never an answer. */
  placeholders: Placeholder[]
}>()

const {t} = useI18n()

/** Everything the template writes except its questions, where an answer can be named. */
const written = computed(() => {
  const {questions: _questions, ...rest} = draft.value
  return JSON.stringify(rest)
})

function add(kind: TemplateQuestionKind) {
  draft.value.questions = [...draft.value.questions, newQuestion(kind, draft.value.questions)]
}

function remove(index: number) {
  draft.value.questions = draft.value.questions.filter((_, at) => at !== index)
}

function reorder(from: number, to: number) {
  draft.value.questions = moveWithin(draft.value.questions, from, to)
}

function takenBesides(index: number): string[] {
  return draft.value.questions.filter((_, at) => at !== index).map(question => question.key)
}

function named(key: string): boolean {
  return written.value.includes(`${ANSWER_PREFIX}${key}}`) || written.value.includes(`${ANSWER_PREFIX}${key}|`)
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="template-questions">
    <SubHeader>{{ t('documentTemplates.questions.title') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('documentTemplates.questions.hint') }}</MutedText>
    <EmptyState v-if="draft.questions.length === 0">{{ t('documentTemplates.questions.empty') }}</EmptyState>
    <DragList :items="draft.questions" :key-fn="(_, index) => index" @reorder="reorder">
      <template #default="{item, index}">
        <QuestionRow :model-value="item" :placeholders="placeholders" :legal="draft.legal" :taken="takenBesides(index)"
                     :named="named(item.key)" class="mb-4 border-b border-(--border) pb-4"
                     @update:model-value="question => draft.questions[index] = question" @remove="remove(index)"/>
      </template>
    </DragList>
    <ButtonRow>
      <SecondaryButton v-for="kind in QUESTION_KINDS" :key="kind" :icon="['fas', 'plus']" :data-testid="`template-question-add-${kind}`"
                       @click="add(kind)">
        {{ t(`documentTemplates.questions.add.${kind}`) }}
      </SecondaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
