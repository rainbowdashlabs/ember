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

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolEvaluation.title')" :subtitle="t('helpCenter.protocolEvaluation.subtitle')">
    <HelpSection :title="t('helpCenter.protocolEvaluation.whatIs')">
      <p>{{ t('helpCenter.protocolEvaluation.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolEvaluation.summaryTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.summaryText') }}</p>
    </HelpSection>

    <!-- Dummy: Evaluation summary -->
    <NeutralContainer>
      <div class="space-y-3 text-sm">
        <div class="flex items-center justify-between font-semibold">
          <span>Lena Müller</span>
          <SuccessBadge>{{ t('helpCenter.protocolEvaluation.passed') }}</SuccessBadge>
        </div>
        <div class="space-y-1">
          <div class="flex justify-between">
            <span>Notruf</span>
            <span class="font-medium text-[var(--success)]">8 / 8 P</span>
          </div>
          <div class="flex justify-between">
            <span>Knoten und Stiche</span>
            <span class="font-medium text-[var(--success)]">10 / 11 P</span>
          </div>
          <div class="flex justify-between">
            <span>Schläuche</span>
            <span class="font-medium text-[var(--error)]">12 / 15 P</span>
          </div>
        </div>
        <div class="flex justify-between border-t border-[var(--border)] pt-2 font-semibold">
          <span>Gesamt</span>
          <span>30 / 34 P (88 %)</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolEvaluation.exportTitle')">
      <p>{{ t('helpCenter.protocolEvaluation.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolEvaluation.tip') }}</HelpTip>
  </HelpArticle>
</template>
