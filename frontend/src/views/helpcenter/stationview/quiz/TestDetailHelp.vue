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
import DummySection from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummySection.vue'
import DummyFrozenQuestions from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyFrozenQuestions.vue'
import DummyGrading from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyGrading.vue'
import DummyGradingDetail from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyGradingDetail.vue'

const {t} = useI18n()
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
      <DummySection class="mt-3" />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.frozenQuestionsTitle')">
      <p>{{ t('helpCenter.quiz.frozenQuestionsText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsSwapText') }}</p>
      <p class="mt-2">{{ t('helpCenter.quiz.frozenQuestionsActivateText') }}</p>
      <DummyFrozenQuestions class="mt-3" />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.gradingTitle')">
      <p>{{ t('helpCenter.quiz.gradingText') }}</p>
      <DummyGrading class="mt-3" />
      <DummyGradingDetail class="mt-3" />
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.accessTitle')">
      <p>{{ t('helpCenter.quiz.accessText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.testDetailTip') }}</HelpTip>
  </HelpArticle>
</template>
