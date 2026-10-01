/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolRunDetail.title')" :subtitle="t('helpCenter.protocolRunDetail.subtitle')">
    <HelpSection :title="t('helpCenter.protocolRunDetail.whatIs')">
      <p>{{ t('helpCenter.protocolRunDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunDetail.membersTitle')">
      <p>{{ t('helpCenter.protocolRunDetail.membersText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="font-semibold text-xs text-[var(--text-muted)] uppercase mb-1">{{ t('helpCenter.sample.protocol.runSpring') }}</div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">{{ t('helpCenter.sample.people.lenaMueller') }}</span>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[var(--text-muted)]">{{ t('helpCenter.sample.protocol.pointsOfSpaced', {points: 32, total: 34}) }}</span>
            <SuccessBadge>{{ t('helpCenter.protocolRunDetail.statusPassed') }}</SuccessBadge>
          </div>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">{{ t('helpCenter.sample.people.maxBauer') }}</span>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[var(--text-muted)]">{{ t('helpCenter.sample.protocol.pointsOfSpaced', {points: 18, total: 34}) }}</span>
            <ErrorBadge>{{ t('helpCenter.protocolRunDetail.statusFailed') }}</ErrorBadge>
          </div>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">{{ t('helpCenter.sample.people.jonasWeber') }}</span>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[var(--text-muted)]">-</span>
            <InfoBadge>{{ t('helpCenter.protocolRunDetail.statusPending') }}</InfoBadge>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolRunDetail.howTo')">
      <p>{{ t('helpCenter.protocolRunDetail.step1') }}</p>
      <p>{{ t('helpCenter.protocolRunDetail.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolRunDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
