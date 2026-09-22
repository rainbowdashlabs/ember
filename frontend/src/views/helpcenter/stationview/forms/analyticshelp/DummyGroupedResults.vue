/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ResultFilterBar from '@/views/stationview/forms/analyticsview/ResultFilterBar.vue'
import GroupedChartsTab from '@/views/stationview/forms/analyticsview/GroupedChartsTab.vue'
import {emptyFilter} from '@/views/stationview/forms/analyticsview/resultQuery'
import {QuestionTypes, ResultDimension, type FormQuestionInfo, type FormResultGroup, type ResultFilter, type ResultGrouping} from '@/api/forms'
import type {MemberGroup, UserTag} from '@/api/types'
import {darkThemeActive} from '@/util/themeState'
import {seriesColor} from '@/util/seriesPalette'

/**
 * The results view's filter bar and grouped charts with example answers: the youth group compared
 * with the active members. The real components, so the help shows exactly what the page does.
 */
const {t} = useI18n()

const groups = computed<MemberGroup[]>(() => [
  {id: 1, name: t('helpCenter.formsAnalytics.dummyGroupYouth')},
  {id: 2, name: t('helpCenter.formsAnalytics.dummyGroupActive')},
  {id: 3, name: t('helpCenter.formsAnalytics.dummyGroupBoard')},
])
const tags = computed<UserTag[]>(() => [{id: 1, stationId: 'wache', name: t('helpCenter.formsAnalytics.dummyTag')}])

const filter = ref<ResultFilter>(emptyFilter())
const grouping = ref<ResultGrouping | null>({by: ResultDimension.GROUP, fieldId: null, only: ['1', '2'], bounds: []})

const questions = computed<FormQuestionInfo[]>(() => [{
  questionId: 1,
  questionType: QuestionTypes.CHOICE,
  title: t('helpCenter.formsAnalytics.dummyQuestion'),
  config: {options: [
    t('helpCenter.formsAnalytics.dummyOptionDrills'),
    t('helpCenter.formsAnalytics.dummyOptionCommunity'),
    t('helpCenter.formsAnalytics.dummyOptionTrips'),
  ]},
}])
const results = computed<FormResultGroup[]>(() => [
  {key: '1', label: t('helpCenter.formsAnalytics.dummyGroupYouth'), responseCount: 8, tallies: [{questionId: 1, answerCount: 8, optionCounts: [2, 3, 5], otherCount: 0}]},
  {key: '2', label: t('helpCenter.formsAnalytics.dummyGroupActive'), responseCount: 12, tallies: [{questionId: 1, answerCount: 12, optionCounts: [9, 4, 1], otherCount: 0}]},
])
const names = computed(() => results.value.map(group => group.label))
const series = computed(() => results.value.map((group, slot) => ({
  key: group.key,
  name: group.label,
  color: seriesColor(slot, darkThemeActive.value) ?? '',
  responseCount: group.responseCount,
})))
</script>

<template>
  <div class="space-y-4">
    <ResultFilterBar v-model:filter="filter" v-model:grouping="grouping" :groups="groups" :tags="tags" :fields="[]"
                     :matching="20" :querying="false"/>
    <GroupedChartsTab :questions="questions" :groups="results" :names="names" :series="series" :overlap="false"/>
  </div>
</template>
