/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { use, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { useI18n } from 'vue-i18n'
import ChartPanel from '@/components/chart/ChartPanel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import { darkThemeActive as isDark } from '@/util/themeState'
import {
  bottomLegend,
  cartesianGrid,
  chartTextColor,
  DONUT_CENTER,
  DONUT_RADIUS,
} from '@/util/chartLayout'
import {QuestionTypes, type FormQuestionInfo, type FormQuestionTally, type FormResultGroup} from '@/api/forms'
import {questionConfig, tallyIn} from './groupedResults'
import {formatDate} from '@/util/format'
use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])
/**
 * One chart per question, drawn from the counts the server made.
 *
 * <p>The counting happens on the server so results can be counted per group of respondents without
 * the member data behind the groups reaching the browser; this only draws.
 *
 * <p>Every chart goes through the shared panel and the shared insets, the way the grouped view and
 * the statistics screens already do. Drawn by hand they had their own heights, their own hard-coded
 * colours and axis labels in a grey that vanished in the dark theme, and a question nobody had
 * answered drew an empty frame with no word about why.
 */
const props = defineProps<{
  questions: FormQuestionInfo[]
  groups: FormResultGroup[]
}>()
const { t } = useI18n()
const textColor = computed(() => chartTextColor(isDark.value))
const RATING = '#FF6421'
const RANKING = '#3694FF'
const LIKERT = '#73CEFF'
function tallyOf(q: FormQuestionInfo): FormQuestionTally | undefined {
  const everyone = props.groups[0]
  return everyone ? tallyIn(everyone, q.questionId) : undefined
}
function answered(q: FormQuestionInfo): boolean {
  return (tallyOf(q)?.answerCount ?? 0) > 0
}
const axis = computed(() => ({axisLabel: {color: textColor.value}}))
function buildChoiceChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const options = (cfg.options as string[]) || []
  const tally = tallyOf(q)
  const data = options.map((opt, i) => ({ name: opt, value: tally?.optionCounts?.[i] ?? 0 }))
  const other = tally?.otherCount ?? 0
  if (cfg.allowOther && other > 0) data.push({ name: t('forms.analytics.otherAnswer'), value: other })
  return {
    tooltip: { trigger: 'item' },
    legend: bottomLegend(textColor.value),
    series: [{
      type: 'pie',
      radius: DONUT_RADIUS,
      center: DONUT_CENTER,
      label: { color: textColor.value },
      data,
      emphasis: { itemStyle: { shadowBlur: 10 } },
    }],
  }
}
function buildRatingChart(q: FormQuestionInfo) {
  const counts = tallyOf(q)?.ratingCounts ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: cartesianGrid(),
    xAxis: { type: 'category', data: counts.map((_, i) => String(i + 1)), ...axis.value },
    yAxis: { type: 'value', ...axis.value },
    series: [{ type: 'bar', data: counts, itemStyle: { color: RATING } }],
  }
}
function buildRankingChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const options = (cfg.options as string[]) || []
  const scores = tallyOf(q)?.rankingScores ?? []
  return {
    tooltip: { trigger: 'axis' },
    grid: cartesianGrid({ rotatedLabels: true }),
    xAxis: { type: 'category', data: options, axisLabel: { color: textColor.value, rotate: 30 } },
    yAxis: { type: 'value', ...axis.value },
    series: [{ type: 'bar', data: scores, itemStyle: { color: RANKING } }],
  }
}
function buildLikertChart(q: FormQuestionInfo) {
  const cfg = questionConfig(q.config)
  const statements = (cfg.statements as string[]) || []
  const scaleMin = (cfg.scaleMin as number) || 1
  const scaleMax = (cfg.scaleMax as number) || 5
  const averages = tallyOf(q)?.statementAverages ?? []
  const names = statements.map((stmt, i) => stmt || `${i + 1}`)
  return {
    tooltip: { trigger: 'axis' },
    grid: cartesianGrid({ rotatedLabels: true }),
    xAxis: { type: 'category', data: names, axisLabel: { color: textColor.value, rotate: 30 } },
    yAxis: { type: 'value', min: scaleMin, max: scaleMax, ...axis.value },
    series: [{ type: 'bar', data: names.map((_, i) => averages[i] ?? null), itemStyle: { color: LIKERT } }],
  }
}
const CHARTED: Record<string, (q: FormQuestionInfo) => EChartsCoreOption> = {
  [QuestionTypes.CHOICE]: buildChoiceChart,
  [QuestionTypes.RATING]: buildRatingChart,
  [QuestionTypes.RANKING]: buildRankingChart,
  [QuestionTypes.LIKERT]: buildLikertChart,
}
function optionFor(q: FormQuestionInfo): EChartsCoreOption {
  return CHARTED[q.questionType]?.(q) ?? {}
}
function isCharted(q: FormQuestionInfo): boolean {
  return q.questionType in CHARTED
}

/**
 * The answers to a question nothing is counted for, as they are read.
 *
 * <p>A date is written the way every other date in the application is written; it arrives as the
 * day the server stored and was being printed in that form, which is not how anybody here reads one.
 */
function answersOf(q: FormQuestionInfo): string[] {
  const values = tallyOf(q)?.values ?? []
  return q.questionType === QuestionTypes.DATE ? values.map(value => formatDate(value)) : values
}
</script>
<template>
  <div class="space-y-6">
    <template v-for="q in questions" :key="q.questionId">
      <ChartPanel
        v-if="isCharted(q)"
        :title="q.title"
        :option="optionFor(q)"
        :has-data="answered(q)"
        :empty-text="t('forms.analytics.noAnswersYet')"
        :height="q.questionType === QuestionTypes.RATING ? 220 : 280"
      >
        <MutedText tag="p" size="sm">
          {{ tallyOf(q)?.answerCount ?? 0 }} {{ t('forms.responses') }}
        </MutedText>
      </ChartPanel>

      <NeutralContainer v-else class="space-y-2">
        <SectionHeader>{{ q.title }}</SectionHeader>
        <MutedText tag="p" size="sm">
          {{ tallyOf(q)?.answerCount ?? 0 }} {{ t('forms.responses') }}
        </MutedText>

        <MutedText v-if="!answered(q)" tag="p" size="sm">{{ t('forms.analytics.noAnswersYet') }}</MutedText>

        <div v-else class="space-y-1 max-h-60 overflow-y-auto">
          <div v-for="(value, i) in answersOf(q)" :key="i"
               class="text-sm px-3 py-2 rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50">{{ value }}</div>
        </div>
      </NeutralContainer>
    </template>
  </div>
</template>
