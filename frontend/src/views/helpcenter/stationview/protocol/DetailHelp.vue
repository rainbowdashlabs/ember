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

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolDetail.title')" :subtitle="t('helpCenter.protocolDetail.subtitle')">
    <HelpSection :title="t('helpCenter.protocolDetail.whatIs')">
      <p>{{ t('helpCenter.protocolDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolDetail.structureTitle')">
      <p>{{ t('helpCenter.protocolDetail.structureText') }}</p>
    </HelpSection>

    <!-- Dummy: Protocol structure with sections and items -->
    <NeutralContainer>
      <div class="space-y-3 text-sm">
        <div class="font-semibold">Jugendflamme Stufe 1</div>
        <div class="ml-2 space-y-2">
          <div>
            <div class="flex items-center gap-2 font-medium">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
              <span>Notruf</span>
              <span class="text-xs text-[var(--text-muted)] ml-auto">8 Punkte</span>
            </div>
            <div class="ml-5 mt-1 space-y-0.5 text-xs text-[var(--text-muted)]">
              <div class="flex justify-between"><span>5 W-Fragen nennen</span><span>5P</span></div>
              <div class="flex justify-between"><span>Notrufnummer 112</span><span>2P</span></div>
              <div class="flex justify-between"><span>Notrufnummer 110</span><span>1P</span></div>
            </div>
          </div>
          <div>
            <div class="flex items-center gap-2 font-medium">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
              <span>Knoten und Stiche</span>
              <span class="text-xs text-[var(--text-muted)] ml-auto">11 Punkte</span>
            </div>
          </div>
          <div>
            <div class="flex items-center gap-2 font-medium">
              <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
              <span>Schläuche</span>
              <span class="text-xs text-[var(--text-muted)] ml-auto">15 Punkte</span>
            </div>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolDetail.thresholdTitle')">
      <p>{{ t('helpCenter.protocolDetail.thresholdText') }}</p>
    </HelpSection>

    <!-- Dummy: Pass threshold display -->
    <NeutralContainer>
      <div class="flex items-center gap-3 text-sm">
        <font-awesome-icon :icon="['fas', 'trophy']" class="text-[var(--primary)]" />
        <span>{{ t('helpCenter.protocolDetail.thresholdLabel') }}</span>
        <span class="font-bold ml-auto">75 %</span>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolDetail.howTo')">
      <p>{{ t('helpCenter.protocolDetail.step1') }}</p>
      <p>{{ t('helpCenter.protocolDetail.step2') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
