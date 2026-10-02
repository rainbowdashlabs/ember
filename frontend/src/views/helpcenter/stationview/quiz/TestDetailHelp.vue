/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import BulletList from '@/components/typography/BulletList.vue'
import DummyLifecycle from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyLifecycle.vue'
import TestSectionsList from '@/views/stationview/quiz/testdetailview/TestSectionsList.vue'
import FrozenQuestionsList from '@/views/stationview/quiz/testdetailview/FrozenQuestionsList.vue'
import TestAttemptList from '@/views/stationview/quiz/testdetailview/TestAttemptList.vue'
import QuestionEvaluationCard from '@/views/stationview/quiz/testevaluateview/QuestionEvaluationCard.vue'
import type {QuizQuestion} from '@/api/generated/schema'
import {
  sampleAttempts,
  sampleCatalogName,
  sampleDraftTest,
  sampleFrozenQuestions,
  sampleMembers,
  sampleReviewAnswer,
  sampleReviewQuestion,
  sampleSections,
} from '@/views/helpcenter/stationview/quiz/fixtures'

const {t} = useI18n()

/** The question type's name, as the detail page shows it. */
function questionTypeName(question: QuizQuestion): string {
  return t(`quiz.questionTypes.${question.quizQuestionType}`)
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.testDetailTitle')" :subtitle="t('helpCenter.quiz.testDetailSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.lifecycleTitle')">
      <p>{{ t('helpCenter.quiz.lifecycleText') }}</p>
      <BulletList class="mt-2">
        <li><strong>{{ t('quiz.statusDraft') }}:</strong> {{ t('helpCenter.quiz.statusDraftDesc') }}</li>
        <li><strong>{{ t('quiz.statusActive') }}:</strong> {{ t('helpCenter.quiz.statusActiveDesc') }}</li>
        <li><strong>{{ t('quiz.statusClosed') }}:</strong> {{ t('helpCenter.quiz.statusClosedDesc') }}</li>
      </BulletList>
      <DummyLifecycle class="mt-3" />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.sectionsTitle')">
      <p>{{ t('helpCenter.quiz.sectionsText') }}</p>
      <TestSectionsList class="mt-3" :sections="sampleSections" :catalog-name="sampleCatalogName"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.frozenQuestionsTitle')">
      <p>{{ t('helpCenter.quiz.frozenQuestionsText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsSwapText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsActivateText') }}</p>
      <FrozenQuestionsList
        class="mt-3"
        :test="sampleDraftTest"
        :frozen-questions="sampleFrozenQuestions"
        :frozen-loading="false"
        :question-type-name="questionTypeName"
      />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.gradingTitle')">
      <p>{{ t('helpCenter.quiz.gradingText') }}</p>
      <TestAttemptList class="mt-3" :test-id="sampleDraftTest.id" :attempts="sampleAttempts" :members="sampleMembers"/>
      <QuestionEvaluationCard
        class="mt-3"
        :question="sampleReviewQuestion"
        :answer="sampleReviewAnswer"
        :index="2"
        :points="1"
        :can-review="false"
        :is-gap-correct="() => false"
      />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.accessTitle')">
      <p>{{ t('helpCenter.quiz.accessText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.testDetailTip') }}</HelpTip>
  </HelpArticle>
</template>
