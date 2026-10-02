/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { VueDraggable, type SortableEvent } from 'vue-draggable-plus'
import { useI18n } from 'vue-i18n'
import QuestionEditor from '../QuestionEditor.vue'
import AddQuestionMenu from '../AddQuestionMenu.vue'
import PageHeaderEditor from './PageHeaderEditor.vue'
import PageRouting from './PageRouting.vue'
import { pageChoices } from '../pageChoice'
import type { FormLayoutEditor } from '../useFormLayout'
import type { QuestionType } from '@/api/forms'

/**
 * One page of the form in the editor: its title and description at the top, its questions, the
 * button that adds one here, and at its end where the reader goes next.
 *
 * <p>A form of one page shows none of what is about pages, only its questions, so a form nobody
 * splits looks the way it always did.
 */
const props = defineProps<{
  layout: FormLayoutEditor
  pageIndex: number
  questionTypes: QuestionType[]
}>()

const { t } = useI18n()

const page = computed(() => props.layout.pages.value[props.pageIndex]!)
const lastPage = computed(() => props.pageIndex === props.layout.pages.value.length - 1)
const otherPages = computed(() => pageChoices(props.layout.pages.value, t, props.pageIndex))
/**
 * A question dragged here from another page stops deciding where its old page leads, the same as one
 * moved here from its menu.
 */
function onDroppedHere(event: SortableEvent) {
  const question = page.value.questions[event.newIndex ?? -1]
  if (question) question.branch = null
}
</script>

<template>
  <section class="space-y-3" :data-testid="`form-page-${pageIndex}`">
    <PageHeaderEditor v-if="layout.paged.value" :page="page" :index="pageIndex" :last="lastPage"
                      :reached="layout.reachable.value.has(page.key)"
                      @update:page="replaced => layout.replacePage(pageIndex, replaced)"
                      @move="direction => layout.movePage(pageIndex, direction)"
                      @remove="layout.removePage(pageIndex)"/>

    <VueDraggable v-model="page.questions" group="form-questions" handle="[data-question-grip]" :animation="150"
                  class="min-h-8 space-y-3" data-testid="page-questions" @add="onDroppedHere">
      <QuestionEditor v-for="(question, index) in page.questions" :key="question.id"
                      :question="question" :number="layout.numberOf(question)"
                      :first="index === 0" :last="index === page.questions.length - 1" :other-pages="otherPages"
                      @update:question="replaced => page.questions.splice(index, 1, replaced)"
                      @move="direction => layout.moveQuestion(pageIndex, index, direction)"
                      @remove="layout.removeQuestion(pageIndex, index)"
                      @move-to-page="target => layout.moveToPage(pageIndex, index, target)"
                      @duplicate="layout.duplicateQuestion(pageIndex, index)"/>
    </VueDraggable>

    <AddQuestionMenu :question-types="questionTypes" @add="type => layout.addQuestion(pageIndex, type)"/>

    <PageRouting v-if="layout.paged.value" :layout="layout" :page-index="pageIndex"/>
  </section>
</template>
