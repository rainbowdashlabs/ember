/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart} from 'echarts/charts'
import {GridComponent, LegendComponent, TooltipComponent} from 'echarts/components'
import VChart from 'vue-echarts'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import {QuestionTypes, type FormQuestionInfo, type FormResultGroup} from '@/api/forms'
import {averageRating, formatValue, matrixOf, tallyIn} from './groupedResults'
import {groupedBarOption, groupedChartHeight, type GroupSeries} from './groupedChart'

use([CanvasRenderer, BarChart, GridComponent, LegendComponent, TooltipComponent])

/**
 * One question's answers compared across groups: a grouped bar chart, or the same numbers as a table
 * when the reader asks for it or there are more groups than colours. Written and date answers are
 * listed under each group's name.
 */
const props = defineProps<{
  question: FormQuestionInfo
  groups: FormResultGroup[]
  names: string[]
  series: GroupSeries[] | null
  asTable: boolean
  textColor: string
}>()

const {t, locale} = useI18n()

const matrix = computed(() => matrixOf(props.question, props.groups, t('forms.analytics.grouping.other')))
const option = computed(() => matrix.value && props.series
    ? groupedBarOption(matrix.value, props.series, props.textColor, locale.value)
    : null)
const height = computed(() => matrix.value && props.series ? groupedChartHeight(matrix.value, props.series) : 0)

const averages = computed(() => props.question.questionType === QuestionTypes.RATING
    ? props.groups.map((group, g) => {
      const tally = tallyIn(group, props.question.questionId)
      const value = formatValue(averageRating(tally), 'average', locale.value)
      return `${props.names[g]}: ${t('forms.analytics.grouping.average', {value})} (${t('forms.analytics.grouping.groupResponses', {count: tally?.answerCount ?? 0})})`
    })
    : [])

function cell(row: number, g: number): string {
  const m = matrix.value
  if (!m) return ''
  const value = formatValue(m.values[row]?.[g] ?? null, m.unit, locale.value)
  return m.unit === 'percent' ? `${value} (${m.counts[row]?.[g] ?? 0})` : value
}

function listed(group: FormResultGroup): string[] {
  return tallyIn(group, props.question.questionId)?.values ?? []
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ question.title }}</SubHeader>

    <template v-if="matrix">
      <VChart v-if="option && !asTable" :option="option" :style="{height: `${height}px`}" autoresize/>
      <DataTable v-else plain>
        <template #head>
          <Th/>
          <Th v-for="(name, g) in names" :key="g" align="right">{{ name }}</Th>
        </template>
        <TRow v-for="(row, r) in matrix.rows" :key="r">
          <Td>{{ row }}</Td>
          <Td v-for="(_, g) in names" :key="g" align="right">{{ cell(r, g) }}</Td>
        </TRow>
        <TRow>
          <Td muted>{{ t('forms.analytics.grouping.answers') }}</Td>
          <Td v-for="(count, g) in matrix.answers" :key="g" muted align="right">{{ count }}</Td>
        </TRow>
      </DataTable>
      <div v-if="averages.length" class="space-y-0.5">
        <MutedText v-for="line in averages" :key="line" size="sm" tag="p">{{ line }}</MutedText>
      </div>
    </template>

    <div v-else class="grid gap-3 sm:grid-cols-2">
      <div v-for="(group, g) in groups" :key="group.key" class="space-y-1">
        <MutedText size="sm" tag="p">{{ names[g] }}</MutedText>
        <MutedText v-if="!listed(group).length" size="sm" tag="p">{{ t('forms.analytics.grouping.noAnswers') }}</MutedText>
        <div v-for="(text, i) in listed(group)" :key="i"
             class="text-sm px-3 py-2 rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50">{{ text }}</div>
      </div>
    </div>
  </NeutralContainer>
</template>
