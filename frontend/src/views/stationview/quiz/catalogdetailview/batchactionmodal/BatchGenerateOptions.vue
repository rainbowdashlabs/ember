/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import {QuizQuestionTypes} from '@/api/quiz'

const {t} = useI18n()

const mcCorrect = defineModel<number>('mcCorrect', {required: true})
const mcWrong = defineModel<number>('mcWrong', {required: true})
const connectPairs = defineModel<number>('connectPairs', {required: true})
const orderMin = defineModel<number>('orderMin', {required: true})
const orderMax = defineModel<number>('orderMax', {required: true})
const fillGaps = defineModel<number>('fillGaps', {required: true})
const fillSentences = defineModel<number>('fillSentences', {required: true})

defineProps<{
  selectedTypes: Set<string>
}>()
</script>

<template>
  <div class="space-y-3">
    <template v-if="selectedTypes.has(QuizQuestionTypes.MULTIPLE_CHOICE)">
      <SubHeader class="text-sm">Multiple Choice</SubHeader>
      <div class="flex items-center gap-3 flex-wrap">
        <div class="flex items-center gap-1">
          <FieldLabel hint>{{ t('quiz.batch.correctCount') }}</FieldLabel>
          <NumberInput v-model="mcCorrect" class="w-14"/>
        </div>
        <div class="flex items-center gap-1">
          <FieldLabel hint>{{ t('quiz.batch.wrongCount') }}</FieldLabel>
          <NumberInput v-model="mcWrong" class="w-14"/>
        </div>
      </div>
    </template>
    <template v-if="selectedTypes.has(QuizQuestionTypes.CONNECT)">
      <SubHeader class="text-sm">{{ t('quiz.questionTypes.CONNECT') }}</SubHeader>
      <div class="flex items-center gap-1">
        <FieldLabel hint>{{ t('quiz.batch.pairCount') }}</FieldLabel>
        <NumberInput v-model="connectPairs" class="w-14"/>
      </div>
    </template>
    <template v-if="selectedTypes.has(QuizQuestionTypes.ORDERING)">
      <SubHeader class="text-sm">{{ t('quiz.questionTypes.ORDERING') }}</SubHeader>
      <div class="flex items-center gap-3 flex-wrap">
        <div class="flex items-center gap-1">
          <FieldLabel hint>Min</FieldLabel>
          <NumberInput v-model="orderMin" class="w-14"/>
        </div>
        <div class="flex items-center gap-1">
          <FieldLabel hint>Max</FieldLabel>
          <NumberInput v-model="orderMax" class="w-14"/>
        </div>
      </div>
    </template>
    <template v-if="selectedTypes.has(QuizQuestionTypes.FILL_IN_THE_BLANK)">
      <SubHeader class="text-sm">{{ t('quiz.questionTypes.FILL_IN_THE_BLANK') }}</SubHeader>
      <div class="flex items-center gap-3 flex-wrap">
        <div class="flex items-center gap-1">
          <FieldLabel hint>{{ t('quiz.batch.gapCount') }}</FieldLabel>
          <NumberInput v-model="fillGaps" class="w-14"/>
        </div>
        <div class="flex items-center gap-1">
          <FieldLabel hint>{{ t('quiz.batch.sentenceCount') }}</FieldLabel>
          <NumberInput v-model="fillSentences" class="w-14"/>
        </div>
      </div>
    </template>
  </div>
</template>
