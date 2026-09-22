/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import ChartsTab from './ChartsTab.vue'
import GroupedChartsTab from './GroupedChartsTab.vue'
import IndividualResponseTab from './IndividualResponseTab.vue'
import type {FormAnalytics, FormResponse} from '@/api/forms'
import {formatAnswerDisplay} from '@/util/formAnswerDisplay'
import type {GroupSeries} from './groupedChart'

/**
 * The two ways to read a form's results: charts per question, plain or compared across groups, and
 * the answers one response at a time.
 *
 * `grouped` says the results were split into groups; `names` and `series` are what those groups are
 * called and drawn as. The individual answers come in already narrowed to the current filter.
 */
defineProps<{
  results: FormAnalytics
  grouped: boolean
  names: string[]
  series: GroupSeries[] | null
  responses: FormResponse[]
  currentResponse: FormResponse | null
  currentResponseIndex: number
  loadingResponse: boolean
  getAnswerForQuestion: (questionId: number) => string
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const {t} = useI18n()

const activeTab = ref('charts')
const tabs = computed(() => [
  {key: 'charts', label: t('forms.analytics.tabCharts')},
  {key: 'individual', label: t('forms.analytics.tabIndividual')},
])
</script>

<template>
  <TabBar v-model="activeTab" :tabs="tabs"/>
  <template v-if="activeTab === 'charts'">
    <GroupedChartsTab v-if="grouped" :questions="results.questions" :groups="results.groups" :names="names"
                      :series="series" :overlap="results.groupsOverlap"/>
    <ChartsTab v-else :questions="results.questions" :groups="results.groups"/>
  </template>
  <IndividualResponseTab
      v-if="activeTab === 'individual'"
      :responses="responses"
      :current-response="currentResponse"
      :current-response-index="currentResponseIndex"
      :questions="results.questions"
      :loading-response="loadingResponse"
      :format-answer="formatAnswerDisplay"
      :get-answer-for-question="getAnswerForQuestion"
      @prev="emit('prev')"
      @next="emit('next')"
  />
</template>
