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
import type { FormQuestionAnalytics } from '@/api/forms'
import { QuestionTypes } from '@/api/forms'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

defineProps<{ questions: FormQuestionAnalytics[] }>()

const { t } = useI18n()

function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try { return JSON.parse(config || '{}') } catch { return {} }
}

function parseValue(value: string): Record<string, unknown> {
  try { return JSON.parse(value || '{}') } catch { return {} }
}

function buildChoiceChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const options = (cfg.options as string[]) || []
  const counts = new Array(options.length).fill(0)
  let otherCount = 0
  for (const v of q.values) {
    const parsed = parseValue(v) as { selected?: number[]; other?: string }
    if (parsed.selected) { for (const idx of parsed.selected) { if (idx < counts.length) counts[idx]++ } }
    if (parsed.other) otherCount++
  }
  const data = options.map((opt, i) => ({ name: opt, value: counts[i] }))
  if (cfg.allowOther && otherCount > 0) data.push({ name: 'Sonstiges', value: otherCount })
  return { tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['30%', '70%'], data, emphasis: { itemStyle: { shadowBlur: 10 } } }] }
}

function buildRatingChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const scale = (cfg.scale as number) || 5
  const counts = new Array(scale).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { rating?: number }
    if (parsed.rating && parsed.rating >= 1 && parsed.rating <= scale) counts[parsed.rating - 1]++
  }
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: Array.from({ length: scale }, (_, i) => String(i + 1)) }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: counts, itemStyle: { color: '#FF6421' } }] }
}

function buildRankingChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const options = (cfg.options as string[]) || []
  const scores = new Array(options.length).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { order?: number[] }
    if (parsed.order) { for (let rank = 0; rank < parsed.order.length; rank++) { scores[parsed.order[rank]] += (parsed.order.length - rank) } }
  }
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: options }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: scores, itemStyle: { color: '#3694FF' } }] }
}

function buildLikertChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const statements = (cfg.statements as string[]) || []
  const scaleMin = (cfg.scaleMin as number) || 1
  const scaleMax = (cfg.scaleMax as number) || 5
  const avgScores = new Array(statements.length).fill(0)
  const counts = new Array(statements.length).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { ratings?: Record<string, number> }
    if (parsed.ratings) { for (const [si, rating] of Object.entries(parsed.ratings)) { const idx = Number(si); if (idx < statements.length) { avgScores[idx] += rating; counts[idx]++ } } }
  }
  const data = statements.map((stmt, i) => ({ name: stmt || `Option ${i + 1}`, value: counts[i] > 0 ? Math.round((avgScores[i] / counts[i]) * 10) / 10 : 0 }))
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: data.map(d => d.name) }, yAxis: { type: 'value', min: scaleMin, max: scaleMax }, series: [{ type: 'bar', data: data.map(d => d.value), itemStyle: { color: '#73CEFF' } }] }
}

function getTextResponses(q: FormQuestionAnalytics): string[] {
  return q.values.map(v => (parseValue(v) as { text?: string }).text).filter((t): t is string => !!t)
}
</script>

<template>
  <div class="space-y-6">
    <NeutralContainer v-for="q in questions" :key="q.questionId">
      <div class="space-y-3">
        <SubHeader>{{ q.title }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ q.values.length }} {{ t('forms.responses') }}</p>
        <VChart v-if="q.questionType === QuestionTypes.CHOICE" :option="buildChoiceChart(q)" autoresize style="height: 250px" />
        <VChart v-if="q.questionType === QuestionTypes.RATING" :option="buildRatingChart(q)" autoresize style="height: 200px" />
        <VChart v-if="q.questionType === QuestionTypes.RANKING" :option="buildRankingChart(q)" autoresize style="height: 250px" />
        <VChart v-if="q.questionType === QuestionTypes.LIKERT" :option="buildLikertChart(q)" autoresize style="height: 250px" />
        <div v-if="q.questionType === QuestionTypes.TEXT" class="space-y-1 max-h-60 overflow-y-auto">
          <div v-for="(text, i) in getTextResponses(q)" :key="i"
               class="text-sm px-3 py-2 rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50">{{ text }}</div>
        </div>
        <div v-if="q.questionType === QuestionTypes.DATE" class="space-y-1">
          <div v-for="(v, i) in q.values" :key="i" class="text-sm text-(--text-muted)">
            {{ (parseValue(v) as { date?: string }).date || '–' }}
          </div>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
