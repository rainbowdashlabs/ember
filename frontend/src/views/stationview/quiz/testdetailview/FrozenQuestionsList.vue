/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {QuizTestStatus, type FrozenQuestionDetail, type QuizQuestion, type QuizTestDetail} from '@/api/quiz'

defineProps<{
  test: QuizTestDetail['test']
  frozenQuestions: FrozenQuestionDetail[]
  frozenLoading: boolean
  questionTypeName: (q: QuizQuestion) => string
}>()

const emit = defineEmits<{
  generate: []
  'random-replace': [position: number]
  'pick-replace': [position: number]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SubHeader>{{ t('quiz.frozenQuestions.title') }} ({{ frozenQuestions.length }})</SubHeader>
      <SecondaryButton v-if="test.status !== QuizTestStatus.ACTIVE" :disabled="frozenLoading" @click="emit('generate')">
        <Spinner v-if="frozenLoading" size="sm" />
        <font-awesome-icon v-else :icon="['fas', 'rotate']" class="mr-1" />
        {{ frozenQuestions.length > 0 ? t('quiz.frozenQuestions.regenerate') : t('quiz.frozenQuestions.generate') }}
      </SecondaryButton>
    </div>
    <EmptyState compact v-if="frozenQuestions.length === 0">{{ t('quiz.frozenQuestions.empty') }}</EmptyState>
    <NeutralContainer v-for="fq in frozenQuestions" :key="fq.position">
      <div v-if="fq.question" class="flex items-start gap-3">
        <MutedText class="w-6 shrink-0 pt-0.5">{{ fq.position + 1 }}.</MutedText>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="text-sm font-medium">{{ fq.question.title }}</span>
            <SecondaryBadge>{{ questionTypeName(fq.question) }}</SecondaryBadge>
            <InfoBadge>{{ fq.question.points }} {{ t('quiz.points') }}</InfoBadge>
          </div>
          <MutedText v-if="fq.question.description" tag="p" class="mt-0.5">{{ fq.question.description }}</MutedText>
        </div>
        <div v-if="test.status === QuizTestStatus.DRAFT" class="flex gap-1 shrink-0">
          <MutedIconButton :icon="['fas', 'shuffle']" :label="t('quiz.frozenQuestions.randomReplace')" @click="emit('random-replace', fq.position)" />
          <MutedIconButton :icon="['fas', 'arrow-right-arrow-left']" :label="t('quiz.frozenQuestions.pickReplace')" @click="emit('pick-replace', fq.position)" />
        </div>
      </div>
      <div v-else class="text-xs text-(--text-muted) italic">{{ t('quiz.frozenQuestions.questionMissing') }}</div>
    </NeutralContainer>
  </div>
</template>
