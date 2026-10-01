/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import AnalyticsHeader from '@/views/stationview/forms/analyticsview/AnalyticsHeader.vue'
import AnalyticsTabs from '@/views/stationview/forms/analyticsview/AnalyticsTabs.vue'
import ResultFilterBar from '@/views/stationview/forms/analyticsview/ResultFilterBar.vue'
import GroupedChartsTab from '@/views/stationview/forms/analyticsview/GroupedChartsTab.vue'
import {emptyFilter} from '@/views/stationview/forms/analyticsview/resultQuery'
import {ResultDimension, type ResultFilterState, type ResultGroupingState} from '@/api/forms'
import {useThemePaint} from '@/composables/useThemePaint'
import {seriesColor} from '@/util/seriesPalette'
import {
  sampleAnswerOf,
  sampleGroupedQuestions,
  sampleGroupedResults,
  sampleGroups,
  sampleResponse,
  sampleResults,
  sampleTags,
} from '@/views/helpcenter/stationview/forms/analyticshelp/fixtures'

const {t} = useI18n()
const {dark: darkThemeActive} = useThemePaint()

const results = sampleResults(t)
const response = sampleResponse()

const filter = ref<ResultFilterState>(emptyFilter())
const grouping = ref<ResultGroupingState | null>({by: ResultDimension.GROUP, fieldId: null, only: ['1', '2'], bounds: []})
const groupedResults = sampleGroupedResults(t)
const groupNames = groupedResults.map(group => group.label)
const groupSeries = computed(() => groupedResults.map((group, slot) => ({
  key: group.key,
  name: group.label,
  color: seriesColor(slot, darkThemeActive.value) ?? '',
  responseCount: group.responseCount,
})))
</script>

<template>
  <HelpArticle :title="t('helpCenter.formsAnalytics.title')" :subtitle="t('helpCenter.formsAnalytics.subtitle')">
    <HelpSection :title="t('helpCenter.formsAnalytics.whatIs')">
      <p>{{ t('helpCenter.formsAnalytics.whatIsText') }}</p>
    </HelpSection>

    <div class="space-y-4">
      <AnalyticsHeader :title="t('helpCenter.sample.forms.survey')" :total-responses="results.totalResponses" can-edit/>
      <AnalyticsTabs :results="results" :grouped="false" :names="[]" :series="null"
                     :responses="[response]" :current-response="response" :current-response-index="0"
                     :loading-response="false" :get-answer-for-question="sampleAnswerOf"/>
    </div>

    <HelpSection :title="t('helpCenter.formsAnalytics.chartsTitle')">
      <p>{{ t('helpCenter.formsAnalytics.chartsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.groupingTitle')">
      <p>{{ t('helpCenter.formsAnalytics.groupingText') }}</p>
      <p>{{ t('helpCenter.formsAnalytics.groupingFilterText') }}</p>
      <p>{{ t('helpCenter.formsAnalytics.groupingCompareText') }}</p>
    </HelpSection>

    <div class="space-y-4">
      <ResultFilterBar v-model:filter="filter" v-model:grouping="grouping" :groups="sampleGroups(t)" :tags="sampleTags(t)"
                       :fields="[]" :matching="20" :querying="false"/>
      <GroupedChartsTab :questions="sampleGroupedQuestions(t)" :groups="groupedResults" :names="groupNames"
                        :series="groupSeries" :overlap="false"/>
    </div>

    <HelpSection :title="t('helpCenter.formsAnalytics.groupingNotesTitle')">
      <p>{{ t('helpCenter.formsAnalytics.groupingNotesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.reachedTitle')">
      <p>{{ t('helpCenter.formsAnalytics.reachedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.responsesTitle')">
      <p>{{ t('helpCenter.formsAnalytics.responsesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsAnalytics.exportTitle')">
      <p>{{ t('helpCenter.formsAnalytics.exportText') }}</p>
      <p>{{ t('helpCenter.formsAnalytics.exportFieldsText') }}</p>
      <p>{{ t('helpCenter.formsAnalytics.exportPathText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsAnalytics.tip') }}</HelpTip>
  </HelpArticle>
</template>
