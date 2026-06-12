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
import HelpList from '@/components/helpcenter/HelpList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminFeedMetrics.title')" :subtitle="t('helpCenter.adminFeedMetrics.subtitle')">
    <HelpSection :title="t('helpCenter.adminFeedMetrics.whatIs')">
      <p>{{ t('helpCenter.adminFeedMetrics.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.summaryTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.summaryText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminFeedMetrics.summaryTotal'),
        t('helpCenter.adminFeedMetrics.summaryRendered'),
        t('helpCenter.adminFeedMetrics.summaryCache'),
        t('helpCenter.adminFeedMetrics.summaryAvg'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.chartsTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.chartsText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminFeedMetrics.chartsByType'),
        t('helpCenter.adminFeedMetrics.chartsHistogram'),
        t('helpCenter.adminFeedMetrics.chartsVolume'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.histogramTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.histogramText') }}</p>
      <!-- Buckets explained with their canonical colour coding so admins recognise it
           when they see it on the live panel. -->
      <NeutralContainer class="mt-2">
        <div class="grid grid-cols-1 sm:grid-cols-5 gap-2 text-sm">
          <div class="space-y-1">
            <div class="h-3 rounded" style="background:#00C507"/>
            <p class="font-mono">&lt; 50ms</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.adminFeedMetrics.bucketFast') }}</p>
          </div>
          <div class="space-y-1">
            <div class="h-3 rounded" style="background:#73CEFF"/>
            <p class="font-mono">50–200ms</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.adminFeedMetrics.bucketGood') }}</p>
          </div>
          <div class="space-y-1">
            <div class="h-3 rounded" style="background:#ffdd1b"/>
            <p class="font-mono">200ms–1s</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.adminFeedMetrics.bucketOk') }}</p>
          </div>
          <div class="space-y-1">
            <div class="h-3 rounded" style="background:#FF6421"/>
            <p class="font-mono">1–5s</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.adminFeedMetrics.bucketSlow') }}</p>
          </div>
          <div class="space-y-1">
            <div class="h-3 rounded" style="background:#ec2929"/>
            <p class="font-mono">≥ 5s</p>
            <p class="text-xs text-(--text-muted)">{{ t('helpCenter.adminFeedMetrics.bucketBad') }}</p>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.statusTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.statusText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminFeedMetrics.status200'),
        t('helpCenter.adminFeedMetrics.status304'),
        t('helpCenter.adminFeedMetrics.status429'),
        t('helpCenter.adminFeedMetrics.status500'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.uaTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.uaText') }}</p>
      <p class="mt-2">{{ t('helpCenter.adminFeedMetrics.uaPrivacy') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFeedMetrics.retentionTitle')">
      <p>{{ t('helpCenter.adminFeedMetrics.retentionText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminFeedMetrics.tip') }}</HelpTip>
  </HelpArticle>
</template>
