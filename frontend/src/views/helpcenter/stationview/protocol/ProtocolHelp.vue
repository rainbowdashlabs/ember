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
import MutedText from '@/components/typography/MutedText.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocol.title')" :subtitle="t('helpCenter.protocol.subtitle')">
    <HelpSection :title="t('helpCenter.protocol.whatIs')">
      <p>{{ t('helpCenter.protocol.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.structureTitle')">
      <p>{{ t('helpCenter.protocol.structureText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="font-medium">{{ t('helpCenter.sample.protocol.jugendflamme1') }}</div>
        <div class="ml-4 space-y-1">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
            <span>{{ t('helpCenter.sample.protocol.emergencyCall') }}</span>
            <MutedText class="ml-auto">8P</MutedText>
          </div>
          <div class="ml-6 space-y-0.5 text-xs text-[var(--text-muted)]">
            <div>{{ t('helpCenter.sample.protocol.fiveWPoints') }}</div>
            <div>{{ t('helpCenter.sample.protocol.numbersPoints') }}</div>
          </div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
            <span>{{ t('helpCenter.sample.protocol.knots') }}</span>
            <MutedText class="ml-auto">11P</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'folder']" class="w-3 h-3 text-[var(--primary)]" />
            <span>{{ t('helpCenter.sample.protocol.hoses') }}</span>
            <MutedText class="ml-auto">15P</MutedText>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocol.runsTitle')">
      <p>{{ t('helpCenter.protocol.runsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.gradingTitle')">
      <p>{{ t('helpCenter.protocol.gradingText') }}</p>
    </HelpSection>

    <NeutralContainer>
      <div class="space-y-2">
        <div class="flex items-center gap-2 p-2 rounded-lg border-2 border-[var(--success)]" style="background: color-mix(in srgb, var(--success) 10%, transparent)">
          <font-awesome-icon :icon="['fas', 'square-check']" class="w-5 h-5 text-[var(--success)]" />
          <span class="text-sm">{{ t('helpCenter.sample.protocol.where') }}</span>
          <MutedText class="ml-auto">1P</MutedText>
        </div>
        <div class="flex items-center gap-2 p-2 rounded-lg border-2 border-[var(--border)]">
          <font-awesome-icon :icon="['fas', 'square']" class="w-5 h-5 text-[var(--text-muted)]" />
          <span class="text-sm">{{ t('helpCenter.sample.protocol.what') }}</span>
          <MutedText class="ml-auto">1P</MutedText>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocol.lockingTitle')">
      <p>{{ t('helpCenter.protocol.lockingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocol.exportTitle')">
      <p>{{ t('helpCenter.protocol.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocol.tip') }}</HelpTip>
  </HelpArticle>
</template>
