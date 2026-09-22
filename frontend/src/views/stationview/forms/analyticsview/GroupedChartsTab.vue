/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import GroupedQuestionResult from './GroupedQuestionResult.vue'
import type {FormQuestionInfo, FormResultGroup} from '@/api/forms'
import {darkThemeActive as isDark} from '@/util/themeState'
import type {GroupSeries} from './groupedChart'

/**
 * Every question's answers compared across the groups the reader split them into.
 *
 * `series` is null when there are more groups than colours to tell them apart; the numbers are then
 * shown as tables, which is also what the reader gets when they ask for the table view.
 */
const props = defineProps<{
  questions: FormQuestionInfo[]
  groups: FormResultGroup[]
  names: string[]
  series: GroupSeries[] | null
  overlap: boolean
}>()

const {t} = useI18n()

const asTable = ref(false)
const textColor = computed(() => (isDark.value ? '#ccc' : '#333'))
const tooMany = computed(() => props.series === null && props.groups.length > 0)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-end gap-2">
      <SecondaryButton v-if="!tooMany" :icon="asTable ? ['fas', 'chart-bar'] : ['fas', 'table-list']" compact
                       @click="asTable = !asTable">
        {{ asTable ? t('forms.analytics.grouping.asChart') : t('forms.analytics.grouping.asTable') }}
      </SecondaryButton>
    </div>
    <Alert v-if="tooMany" variant="info">{{ t('forms.analytics.grouping.tooMany') }}</Alert>
    <Alert v-if="overlap" variant="info">{{ t('forms.analytics.grouping.overlap') }}</Alert>
    <GroupedQuestionResult
        v-for="q in questions" :key="q.questionId"
        :question="q"
        :groups="groups"
        :names="names"
        :series="series"
        :as-table="asTable || tooMany"
        :text-color="textColor"
    />
  </div>
</template>
