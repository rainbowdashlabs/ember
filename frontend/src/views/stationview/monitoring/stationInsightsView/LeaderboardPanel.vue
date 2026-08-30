/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageLeaderboardTable from '@/views/stationview/monitoring/stationInsightsView/PageLeaderboardTable.vue'
import type {PageLeaderboardEntry} from '@/api/insights'

defineProps<{
  loading: boolean
  rows: PageLeaderboardEntry[]
  includeBots: boolean
  selectedPageId: number | null
}>()

defineEmits<{
  (e: 'select', pageId: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SectionHeader>{{ t('insights.leaderboard.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('insights.leaderboard.subtitle') }}</MutedText>
    <Spinner v-if="loading"/>
    <PageLeaderboardTable
        v-else
        :include-bots="includeBots"
        :rows="rows"
        :selected-page-id="selectedPageId"
        @select="(id: number) => $emit('select', id)"/>
  </NeutralContainer>
</template>
