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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

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
    <HelpSection :title="t('helpCenter.protocolRunList.exampleTitle')">
      <div class="flex items-center justify-between mb-3">
        <SectionHeader>{{ t('protocol.runs') }}</SectionHeader>
        <PrimaryButton disabled>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.createRun') }}
        </PrimaryButton>
      </div>

      <div class="space-y-2">
        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium">Jugendflamme Stufe 1 — Fruehjahr 2025</div>
              <div class="text-sm text-[var(--text-muted)]">Jugendflamme Stufe 1 &mdash; 12.04.2025</div>
            </div>
            <PrimaryBadge>{{ t('protocol.open') }}</PrimaryBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium">Jugendflamme Stufe 2 — Herbst 2024</div>
              <div class="text-sm text-[var(--text-muted)]">Jugendflamme Stufe 2 &mdash; 05.11.2024</div>
            </div>
            <SuccessBadge>{{ t('protocol.closed') }}</SuccessBadge>
          </div>
        </NeutralContainer>

        <NeutralContainer class="cursor-pointer">
          <div class="flex items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium">Leistungsspange — Herbst 2024</div>
              <div class="text-sm text-[var(--text-muted)]">Leistungsspange &mdash; 18.10.2024</div>
            </div>
            <SuccessBadge>{{ t('protocol.closed') }}</SuccessBadge>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolRunList.howTo')">
      <p>{{ t('helpCenter.protocolRunList.step1') }}</p>
      <p>{{ t('helpCenter.protocolRunList.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolRunList.tip') }}</HelpTip>
  </HelpArticle>
</template>
