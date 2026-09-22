/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {QuestionTypes, type FormQuestionInfo, type FormQuestionTally, type FormResultGroup} from '@/api/forms'
import {questionConfig, tallyIn} from './groupedResults'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

/**
 * One chart per question, drawn from the counts the server made.
 *
 * <p>The counting happens on the server so results can be counted per group of respondents without
 * the member data behind the groups reaching the browser; this only draws.
 */
const props = defineProps<{
  questions: FormQuestionInfo[]
  groups: FormResultGroup[]
}>()

const { t } = useI18n()

function tallyOf(q: FormQuestionInfo): FormQuestionTally | undefined {
  const everyone = props.groups[0]
  return everyone ? tallyIn(everyone, q.questionId) : undefined
}

function buildChoiceChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const options = (cfg.options as string[]) || []
  const tally = tallyOf(q)
  const data = options.map((opt, i) => ({ name: opt, value: tally?.optionCounts?.[i] ?? 0 }))
  const other = tally?.otherCount ?? 0
  if (cfg.allowOther && other > 0) data.push({ name: 'Sonstiges', value: other })
  return { tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['30%', '70%'], data, emphasis: { itemStyle: { shadowBlur: 10 } } }] }
}

function buildRatingChart(q: FormQuestionInfo) {
  const counts = tallyOf(q)?.ratingCounts ?? []
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: counts.map((_, i) => String(i + 1)) }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: counts, itemStyle: { color: '#FF6421' } }] }
}

function buildRankingChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const options = (cfg.options as string[]) || []
  const scores = tallyOf(q)?.rankingScores ?? []
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: options }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: scores, itemStyle: { color: '#3694FF' } }] }
}

function buildLikertChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const statements = (cfg.statements as string[]) || []
  const scaleMin = (cfg.scaleMin as number) || 1
  const scaleMax = (cfg.scaleMax as number) || 5
  const averages = tallyOf(q)?.statementAverages ?? []
  const names = statements.map((stmt, i) => stmt || `Option ${i + 1}`)
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: names }, yAxis: { type: 'value', min: scaleMin, max: scaleMax }, series: [{ type: 'bar', data: names.map((_, i) => averages[i] ?? null), itemStyle: { color: '#73CEFF' } }] }
}
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer v-for="q in questions" :key="q.questionId">
      <div class="space-y-3">
        <SubHeader>{{ q.title }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ tallyOf(q)?.answerCount ?? 0 }} {{ t('forms.responses') }}</p>
        <VChart v-if="q.questionType === QuestionTypes.CHOICE" :option="buildChoiceChart(q)" autoresize style="height: 250px" />
        <VChart v-if="q.questionType === QuestionTypes.RATING" :option="buildRatingChart(q)" autoresize style="height: 200px" />
        <VChart v-if="q.questionType === QuestionTypes.RANKING" :option="buildRankingChart(q)" autoresize style="height: 250px" />
        <VChart v-if="q.questionType === QuestionTypes.LIKERT" :option="buildLikertChart(q)" autoresize style="height: 250px" />
        <div v-if="q.questionType === QuestionTypes.TEXT" class="space-y-1 max-h-60 overflow-y-auto">
          <div v-for="(text, i) in tallyOf(q)?.values ?? []" :key="i"
               class="text-sm px-3 py-2 rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50">{{ text }}</div>
        </div>
        <div v-if="q.questionType === QuestionTypes.DATE" class="space-y-1">
          <div v-for="(date, i) in tallyOf(q)?.values ?? []" :key="i" class="text-sm text-(--text-muted)">
            {{ date }}
          </div>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
