/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ResultFilterBar from './ResultFilterBar.vue'
import MissingResponsesPanel from './MissingResponsesPanel.vue'
import AnalyticsTabs from './AnalyticsTabs.vue'
import type {Form, FormAnalytics, FormResponse} from '@/api/forms'
import type {MemberIdentity} from '@/api/types'

/**
 * The answers themselves: how they are narrowed, who is missing, and what they add up to.
 *
 * <p>Separate from the header and the link above it because those two are about the form while this
 * is about what came back, and because the screen holding all of it had grown past what one template
 * should carry.
 */
defineProps<{
  form: Form
  analytics: FormAnalytics
  shown: FormAnalytics | null
  /** Everything the narrowing needs, as the result view hands it over. */
  view: ReturnType<typeof import('./useResultView').useResultView>
  groupable: boolean
  groupNames: string[]
  visibleResponses: FormResponse[]
  currentResponse: FormResponse | null
  currentResponseIndex: number
  loadingResponse: boolean
  getAnswerForQuestion: (questionId: number) => string
  missing: MemberIdentity[]
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()

const {t} = useI18n()
</script>

<template>
  <ResultFilterBar
    v-if="groupable && analytics.totalResponses > 0"
    v-model:filter="view.filter.value"
    v-model:grouping="view.grouping.value"
    :groups="view.groups.value"
    :tags="view.tags.value"
    :fields="view.groupableFields.value"
    :matching="view.narrowed.value ? view.narrowed.value.totalResponses : null"
    :querying="view.querying.value"
    @reset="view.reset"
  />

  <MissingResponsesPanel v-if="form.forced && missing.length > 0" :members="missing"/>

  <EmptyState v-if="analytics.totalResponses === 0">{{ t('forms.analytics.noResponses') }}</EmptyState>

  <AnalyticsTabs
    v-else-if="shown"
    :results="shown"
    :grouped="!!(view.grouping.value && view.narrowed.value)"
    :names="groupNames"
    :series="view.series.value"
    :responses="visibleResponses"
    :current-response="currentResponse"
    :current-response-index="currentResponseIndex"
    :loading-response="loadingResponse"
    :get-answer-for-question="getAnswerForQuestion"
    @prev="emit('prev')"
    @next="emit('next')"
  />
</template>
