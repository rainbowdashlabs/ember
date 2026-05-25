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
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quizCatalogGenerate.title')" :subtitle="t('helpCenter.quizCatalogGenerate.subtitle')">
    <HelpSection :title="t('helpCenter.quizCatalogGenerate.whatIs')">
      <p>{{ t('helpCenter.quizCatalogGenerate.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogGenerate.howTo')">
      <p>{{ t('helpCenter.quizCatalogGenerate.step1') }}</p>
      <p>{{ t('helpCenter.quizCatalogGenerate.step2') }}</p>
      <p>{{ t('helpCenter.quizCatalogGenerate.step3') }}</p>
      <p>{{ t('helpCenter.quizCatalogGenerate.step4') }}</p>
    </HelpSection>

    <!-- Dummy: Generate form -->
    <NeutralContainer>
      <div class="space-y-3 text-sm">
        <div class="text-xs text-[var(--text-muted)] uppercase font-semibold">{{ t('helpCenter.quizCatalogGenerate.previewLabel') }}</div>
        <div class="flex items-center gap-2 p-2 rounded border border-[var(--border)]">
          <span class="flex-1 font-medium">Was ist der Unterschied zwischen Saug- und Druckschlauch?</span>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'rotate']" />
          </SecondaryButton>
        </div>
        <div class="flex items-center gap-2 p-2 rounded border border-[var(--border)]">
          <span class="flex-1 font-medium">Welche Notrufnummer gilt in Deutschland?</span>
          <SecondaryButton disabled>
            <font-awesome-icon :icon="['fas', 'rotate']" />
          </SecondaryButton>
        </div>
        <PrimaryButton disabled class="w-full justify-center">
          {{ t('helpCenter.quizCatalogGenerate.acceptButton') }}
        </PrimaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizCatalogGenerate.apiKeyTitle')">
      <p>{{ t('helpCenter.quizCatalogGenerate.apiKeyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogGenerate.tip') }}</HelpTip>
  </HelpArticle>
</template>
