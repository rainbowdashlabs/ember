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
  <HelpArticle :title="t('helpCenter.protocolGrading.title')" :subtitle="t('helpCenter.protocolGrading.subtitle')">
    <HelpSection :title="t('helpCenter.protocolGrading.whatIs')">
      <p>{{ t('helpCenter.protocolGrading.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.howTo')">
      <p>{{ t('helpCenter.protocolGrading.step1') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step2') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step3') }}</p>
    </HelpSection>

    <!-- Dummy: Grading view with checkboxes -->
    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="font-semibold mb-1">Notruf</div>
        <div class="flex items-center gap-2 p-2 rounded-lg border-2 border-[var(--success)]" style="background: color-mix(in srgb, var(--success) 10%, transparent)">
          <font-awesome-icon :icon="['fas', 'square-check']" class="w-5 h-5 text-[var(--success)]" />
          <span>5 W-Fragen nennen</span>
          <MutedText class="ml-auto">5P</MutedText>
        </div>
        <div class="flex items-center gap-2 p-2 rounded-lg border-2 border-[var(--success)]" style="background: color-mix(in srgb, var(--success) 10%, transparent)">
          <font-awesome-icon :icon="['fas', 'square-check']" class="w-5 h-5 text-[var(--success)]" />
          <span>Notrufnummer 112</span>
          <MutedText class="ml-auto">2P</MutedText>
        </div>
        <div class="flex items-center gap-2 p-2 rounded-lg border-2 border-[var(--border)]">
          <font-awesome-icon :icon="['fas', 'square']" class="w-5 h-5 text-[var(--text-muted)]" />
          <span>Notrufnummer 110</span>
          <MutedText class="ml-auto">1P</MutedText>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.protocolGrading.lockTitle')">
      <p>{{ t('helpCenter.protocolGrading.lockText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolGrading.tip') }}</HelpTip>
  </HelpArticle>
</template>
