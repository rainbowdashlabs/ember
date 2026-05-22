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
  <HelpArticle :title="t('helpCenter.quiz.ai.title')" :subtitle="t('helpCenter.quiz.ai.subtitle')">
    <HelpSection :title="t('helpCenter.quiz.ai.whatIs')">
      <p>{{ t('helpCenter.quiz.ai.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.ai.howTo')">
      <div class="space-y-2 text-sm">
        <p>{{ t('helpCenter.quiz.ai.step1') }}</p>
        <p>{{ t('helpCenter.quiz.ai.step2') }}</p>
        <p>{{ t('helpCenter.quiz.ai.step3') }}</p>
        <p>{{ t('helpCenter.quiz.ai.step4') }}</p>
        <p>{{ t('helpCenter.quiz.ai.step5') }}</p>
      </div>
    </HelpSection>

    <!-- Dummy: Generation config -->
    <NeutralContainer>
      <div class="space-y-3">
        <div class="flex items-center gap-3 p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <span class="text-xs text-(--text-muted) flex-1">Multiple Choice</span>
          <span class="text-xs font-medium w-10 text-center">5</span>
          <span class="text-xs text-(--text-muted) flex-1">Grundlagen</span>
        </div>
        <div class="flex items-center gap-3 p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <span class="text-xs text-(--text-muted) flex-1">Wahr/Falsch</span>
          <span class="text-xs font-medium w-10 text-center">3</span>
          <span class="text-xs text-(--text-muted) flex-1">Ausrüstung</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quiz.ai.providers')">
      <p>{{ t('helpCenter.quiz.ai.providersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.ai.apiKey')">
      <p>{{ t('helpCenter.quiz.ai.apiKeyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quiz.ai.tip') }}</HelpTip>
  </HelpArticle>
</template>
