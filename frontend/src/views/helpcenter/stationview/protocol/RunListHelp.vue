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

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolRunList.title')" :subtitle="t('helpCenter.protocolRunList.subtitle')">
    <HelpSection :title="t('helpCenter.protocolRunList.whatIs')">
      <p>{{ t('helpCenter.protocolRunList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunList.statusTitle')">
      <p>{{ t('helpCenter.protocolRunList.statusText') }}</p>
    </HelpSection>

    <!-- Dummy: Run list -->
    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <div>
            <div class="font-medium">Jugendflamme Stufe 1 — Frühjahr 2025</div>
            <div class="text-xs text-[var(--text-muted)]">12.04.2025 · 8 Mitglieder</div>
          </div>
          <InfoBadge>{{ t('helpCenter.protocolRunList.statusActive') }}</InfoBadge>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <div>
            <div class="font-medium">Jugendflamme Stufe 2 — Herbst 2024</div>
            <div class="text-xs text-[var(--text-muted)]">05.11.2024 · 5 Mitglieder</div>
          </div>
          <SuccessBadge>{{ t('helpCenter.protocolRunList.statusDone') }}</SuccessBadge>
        </div>
        <div class="flex items-center justify-between p-2 rounded border border-[var(--border)]">
          <div>
            <div class="font-medium">Leistungsspange — Herbst 2024</div>
            <div class="text-xs text-[var(--text-muted)]">18.10.2024 · 3 Mitglieder</div>
          </div>
          <SuccessBadge>{{ t('helpCenter.protocolRunList.statusDone') }}</SuccessBadge>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolRunList.howTo')">
      <p>{{ t('helpCenter.protocolRunList.step1') }}</p>
      <p>{{ t('helpCenter.protocolRunList.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolRunList.tip') }}</HelpTip>
  </HelpArticle>
</template>
