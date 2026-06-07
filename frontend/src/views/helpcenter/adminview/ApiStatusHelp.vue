/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApiStatus.title')" :subtitle="t('helpCenter.adminApiStatus.subtitle')">
    <HelpSection :title="t('helpCenter.adminApiStatus.whatIs')">
      <p>{{ t('helpCenter.adminApiStatus.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.summaryTitle')">
      <p>{{ t('helpCenter.adminApiStatus.summaryText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.metricsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.metricsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatus.tabsTitle')">
      <p>{{ t('helpCenter.adminApiStatus.tabsText') }}</p>
    </HelpSection>

    <!-- Dummy: API Status page -->
    <HelpSection :title="t('helpCenter.adminApiStatus.exampleTitle')">
      <!-- Summary cards -->
      <div class="grid grid-cols-3 gap-3 mb-4">
        <NeutralContainer class="text-center">
          <p class="text-2xl font-bold">4.832</p>
          <p class="text-xs text-(--text-muted)">{{ t('apiStatus.totalRequests') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <p class="text-2xl font-bold">18ms</p>
          <p class="text-xs text-(--text-muted)">{{ t('apiStatus.avgResponseTime') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center">
          <p class="text-2xl font-bold">3</p>
          <p class="text-xs text-(--text-muted)">{{ t('apiStatus.serverErrors') }}</p>
        </NeutralContainer>
      </div>

      <!-- Chart placeholders -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 mb-4">
        <NeutralContainer>
          <p class="text-sm font-semibold mb-2">{{ t('apiStatus.requestsOverTime') }}</p>
          <div class="h-24 flex items-center justify-center text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl" />
          </div>
        </NeutralContainer>
        <NeutralContainer>
          <p class="text-sm font-semibold mb-2">{{ t('apiStatus.avgResponseTime') }}</p>
          <div class="h-24 flex items-center justify-center text-(--text-muted)">
            <font-awesome-icon :icon="['fas', 'chart-line']" class="text-2xl" />
          </div>
        </NeutralContainer>
      </div>

      <!-- Tabs -->
      <div class="flex gap-2 mb-4">
        <SelectionToggleButton :selected="true">
          {{ t('apiStatus.slowest') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false">
          {{ t('apiStatus.fastest') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false">
          {{ t('apiStatus.failing') }}
        </SelectionToggleButton>
      </div>

      <!-- Endpoint table with correct columns -->
      <NeutralContainer class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-(--text-muted) border-b border-[var(--border)]">
              <th class="py-2 pr-3">{{ t('apiStatus.endpoint') }}</th>
              <th class="py-2 pr-3 text-right">{{ t('apiStatus.count') }}</th>
              <th class="py-2 pr-3 text-right">{{ t('apiStatus.avg') }}</th>
              <th class="py-2 pr-3 text-right">Min</th>
              <th class="py-2 pr-3 text-right">Max</th>
              <th class="py-2 text-right">{{ t('apiStatus.errorRate') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-[var(--border)] cursor-pointer hover:bg-[var(--bg-accent)]/10">
              <td class="py-2 pr-3 font-mono text-xs">
                <span class="font-semibold mr-1 text-green-600">GET</span>/api/v1/kb/search
              </td>
              <td class="py-2 pr-3 text-right tabular-nums">94</td>
              <td class="py-2 pr-3 text-right tabular-nums">210ms</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">45ms</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">2340ms</td>
              <td class="py-2 text-right tabular-nums">0.0%</td>
            </tr>
            <tr class="border-b border-[var(--border)] cursor-pointer hover:bg-[var(--bg-accent)]/10">
              <td class="py-2 pr-3 font-mono text-xs">
                <span class="font-semibold mr-1 text-blue-600">POST</span>/api/v1/quiz/submit
              </td>
              <td class="py-2 pr-3 text-right tabular-nums">382</td>
              <td class="py-2 pr-3 text-right tabular-nums">45ms</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">12ms</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">180ms</td>
              <td class="py-2 text-right tabular-nums">0.0%</td>
            </tr>
            <tr class="border-b border-[var(--border)] last:border-0 cursor-pointer hover:bg-[var(--bg-accent)]/10">
              <td class="py-2 pr-3 font-mono text-xs">
                <span class="font-semibold mr-1 text-blue-600">POST</span>/api/v1/mail/send
              </td>
              <td class="py-2 pr-3 text-right tabular-nums">3</td>
              <td class="py-2 pr-3 text-right tabular-nums">—</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">—</td>
              <td class="py-2 pr-3 text-right tabular-nums text-(--text-muted)">—</td>
              <td class="py-2 text-right tabular-nums text-red-500 font-semibold">100.0%</td>
            </tr>
          </tbody>
        </table>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApiStatus.tip') }}</HelpTip>
  </HelpArticle>
</template>
