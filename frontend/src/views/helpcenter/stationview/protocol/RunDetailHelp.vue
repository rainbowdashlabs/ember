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

    <!-- Dummy: Member progress table -->
    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="font-semibold text-xs text-[var(--text-muted)] uppercase mb-1">Jugendflamme Stufe 1 - Frühjahr 2025</div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">Lena Müller</span>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[var(--text-muted)]">32 / 34 P</span>
            <SuccessBadge>{{ t('helpCenter.protocolRunDetail.statusPassed') }}</SuccessBadge>
          </div>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">Max Bauer</span>
          <div class="flex items-center gap-2">
            <span class="text-xs text-[var(--text-muted)]">18 / 34 P</span>
            <ErrorBadge>{{ t('helpCenter.protocolRunDetail.statusFailed') }}</ErrorBadge>
          </div>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <span class="font-medium">Jonas Weber</span>
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
