/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DummyFrozenQuestion from '@/views/helpcenter/stationview/quiz/testdetailhelp/DummyFrozenQuestion.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between mb-2">
        <span class="font-semibold text-sm">{{ t('helpCenter.quizTestDetailFrozenQuestions.heading') }}</span>
        <SecondaryButton :icon="['fas', 'rotate']" disabled>
          {{ t('helpCenter.quizTestDetailFrozenQuestions.generateButton') }}
        </SecondaryButton>
      </div>
      <DummyFrozenQuestion :index="1" :text="t('helpCenter.quizTestDetailFrozenQuestions.question1Text')" :type="t('helpCenter.quizTestDetailFrozenQuestions.question1Type')" :points="t('helpCenter.quizTestDetailFrozenQuestions.question1Points')" />
      <DummyFrozenQuestion :index="2" :text="t('helpCenter.quizTestDetailFrozenQuestions.question2Text')" :type="t('helpCenter.quizTestDetailFrozenQuestions.question2Type')" :points="t('helpCenter.quizTestDetailFrozenQuestions.question2Points')" />
      <DummyFrozenQuestion :index="3" :text="t('helpCenter.quizTestDetailFrozenQuestions.question3Text')" :type="t('helpCenter.quizTestDetailFrozenQuestions.question3Type')" :points="t('helpCenter.quizTestDetailFrozenQuestions.question3Points')" />
    </div>
  </NeutralContainer>
</template>
