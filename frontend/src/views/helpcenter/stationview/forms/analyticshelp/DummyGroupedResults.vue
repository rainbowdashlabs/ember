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
import {QuestionTypes, ResultDimension, type ResultFilterState, type ResultGroupingState} from '@/api/forms'
import type {FormQuestionInfo, FormResultGroup, MemberGroup, UserTag} from '@/api/generated/schema'
import {useThemePaint} from '@/composables/useThemePaint'
import {seriesColor} from '@/util/seriesPalette'
import {numberedOptions} from '@/util/formOptions'

/**
 * The results view's filter bar and grouped charts with example answers: the youth group compared
 * with the active members. The real components, so the help shows exactly what the page does.
 */
const {t} = useI18n()
const {dark: darkThemeActive} = useThemePaint()

const groups = computed<MemberGroup[]>(() => [
  dummyGroup(1, t('helpCenter.formsAnalytics.dummyGroupYouth')),
  dummyGroup(2, t('helpCenter.formsAnalytics.dummyGroupActive')),
  dummyGroup(3, t('helpCenter.formsAnalytics.dummyGroupBoard')),
])
const tags = computed<UserTag[]>(() => [
  {id: 1, stationId: 'wache', name: t('helpCenter.formsAnalytics.dummyTag'), color: null, visible: true, position: 0},
])

function dummyGroup(id: number, name: string): MemberGroup {
  return {id, stationId: 'wache', name, color: null, position: id, groupSetId: null, userTypes: []}
}

const filter = ref<ResultFilterState>(emptyFilter())
const grouping = ref<ResultGroupingState | null>({by: ResultDimension.GROUP, fieldId: null, only: ['1', '2'], bounds: []})

const questions = computed<FormQuestionInfo[]>(() => [{
  questionId: 1,
  questionType: QuestionTypes.CHOICE,
  title: t('helpCenter.formsAnalytics.dummyQuestion'),
  config: {questionType: QuestionTypes.CHOICE, options: numberedOptions(
    t('helpCenter.formsAnalytics.dummyOptionDrills'),
    t('helpCenter.formsAnalytics.dummyOptionCommunity'),
    t('helpCenter.formsAnalytics.dummyOptionTrips'),
  )},
}])
const results = computed<FormResultGroup[]>(() => [
  {key: '1', label: t('helpCenter.formsAnalytics.dummyGroupYouth'), responseCount: 8, tallies: [{questionId: 1, answerCount: 8, optionCounts: {o0: 2, o1: 3, o2: 5}, otherCount: 0}]},
  {key: '2', label: t('helpCenter.formsAnalytics.dummyGroupActive'), responseCount: 12, tallies: [{questionId: 1, answerCount: 12, optionCounts: {o0: 9, o1: 4, o2: 1}, otherCount: 0}]},
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
