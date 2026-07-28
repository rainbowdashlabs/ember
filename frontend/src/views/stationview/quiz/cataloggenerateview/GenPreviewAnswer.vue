/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'

const props = defineProps<{
  quizQuestionType: string
  config: string
}>()

const {t} = useI18n()

const cfg = computed<Record<string, unknown>>(() => {
  try { return JSON.parse(props.config) } catch { return {} }
})

const mcOptions = computed(() => (cfg.value.options as { text: string; correct: boolean }[]) ?? [])
const tfCorrect = computed(() => cfg.value.correctAnswer as boolean)
const freeAnswers = computed(() => (cfg.value.answers as string[]) ?? [])
const fillAnswers = computed(() => (cfg.value.answers as string[]) ?? [])
const fillDistractors = computed(() => (cfg.value.distractors as string[]) ?? [])
const connectPairs = computed(() => (cfg.value.pairs as { left: string; right: string }[]) ?? [])
const orderingItems = computed(() => (cfg.value.items as string[]) ?? [])
</script>

<template>
  <template v-if="quizQuestionType === 'MULTIPLE_CHOICE'">
    <div class="mt-1 space-y-0.5">
      <div v-for="(opt, oi) in mcOptions" :key="oi" class="flex items-center gap-1 text-xs">
        <font-awesome-icon :icon="['fas', opt.correct ? 'square-check' : 'square']"
                           :class="opt.correct ? 'text-success' : 'text-(--text-muted)'"
                           class="text-[10px]"/>
        <span :class="opt.correct ? 'font-medium' : 'text-(--text-muted)'">{{ opt.text }}</span>
      </div>
    </div>
  </template>
  <template v-else-if="quizQuestionType === 'TRUE_FALSE'">
    <p class="text-xs mt-1" :class="tfCorrect ? 'text-success' : 'text-error'">
      {{ tfCorrect ? t('quiz.trueLabel') : t('quiz.falseLabel') }}
    </p>
  </template>
  <template v-else-if="quizQuestionType === 'FREE_ANSWER'">
    <MutedText v-for="(ans, ai2) in freeAnswers" :key="ai2" tag="p" class="mt-0.5">
      {{ ai2 + 1 }}. {{ ans }}
    </MutedText>
  </template>
  <template v-else-if="quizQuestionType === 'FILL_IN_THE_BLANK'">
    <p class="text-xs text-success mt-1">{{ fillAnswers.join(', ') }}</p>
    <p v-if="fillDistractors.length > 0" class="text-xs text-error mt-0.5">{{ fillDistractors.join(', ') }}</p>
  </template>
  <template v-else-if="quizQuestionType === 'CONNECT'">
    <div class="mt-1 space-y-0.5">
      <MutedText v-for="(pair, pi) in connectPairs" :key="pi" tag="p">
        {{ pair.left }} &rarr; {{ pair.right }}
      </MutedText>
    </div>
  </template>
  <template v-else-if="quizQuestionType === 'ORDERING'">
    <div class="mt-1 space-y-0.5">
      <MutedText v-for="(item, ii) in orderingItems" :key="ii" tag="p">
        {{ ii + 1 }}. {{ item }}
      </MutedText>
    </div>
  </template>
</template>
