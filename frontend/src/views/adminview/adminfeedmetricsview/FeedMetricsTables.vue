/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {FeedUserAgentStat} from '@/api/feedMetrics'

defineProps<{
  statusBreakdown: Array<[number, number]>
  userAgents: FeedUserAgentStat[]
}>()

const {t, n} = useI18n()
</script>

<template>
  <div class="contents">
    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('feedMetrics.statusBreakdown') }}</SectionHeader>
      <table v-if="statusBreakdown.length > 0" class="w-full text-sm">
        <thead>
        <tr class="text-left text-(--text-muted)">
          <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.status') }}</th>
          <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.count') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="[status, count] in statusBreakdown" :key="status" class="border-t border-(--border)">
          <td class="py-1 pr-3 font-mono">{{ status }}</td>
          <td class="py-1 pr-3">{{ n(count) }}</td>
        </tr>
        </tbody>
      </table>
      <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
    </NeutralContainer>

    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('feedMetrics.topUserAgents') }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('feedMetrics.topUserAgentsHint') }}</MutedText>
      <table v-if="userAgents.length > 0" class="w-full text-sm">
        <thead>
        <tr class="text-left text-(--text-muted)">
          <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.userAgent') }}</th>
          <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.requests') }}</th>
          <th class="py-1 pr-3 font-medium">{{ t('feedMetrics.lastSeen') }}</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="ua in userAgents" :key="ua.uaHash" class="border-t border-(--border) align-top">
          <td class="py-1 pr-3 font-mono break-all">{{ ua.uaString }}</td>
          <td class="py-1 pr-3 whitespace-nowrap">{{ n(ua.requestCount) }}</td>
          <td class="py-1 pr-3 whitespace-nowrap">{{ new Date(ua.lastSeen).toLocaleString() }}</td>
        </tr>
        </tbody>
      </table>
      <MutedText v-else tag="div" size="sm">{{ t('feedMetrics.noData') }}</MutedText>
    </NeutralContainer>
  </div>
</template>
