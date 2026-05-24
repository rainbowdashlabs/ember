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
  <HelpArticle :title="t('helpCenter.quizCatalogImport.title')" :subtitle="t('helpCenter.quizCatalogImport.subtitle')">
    <HelpSection :title="t('helpCenter.quizCatalogImport.whatIs')">
      <p>{{ t('helpCenter.quizCatalogImport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quizCatalogImport.formatTitle')">
      <p>{{ t('helpCenter.quizCatalogImport.formatText') }}</p>
    </HelpSection>

    <!-- Dummy: CSV example -->
    <NeutralContainer>
      <div class="font-mono text-xs text-[var(--text-muted)] space-y-0.5">
        <div>type,question,answer1,answer2,answer3,correct</div>
        <div>MULTIPLE_CHOICE,Notrufnummer?,110,112,118,2</div>
        <div>TRUE_FALSE,Wasser leitet Strom?,true,false,,1</div>
        <div>FREE_ANSWER,Was bedeutet ABC?,,,, </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.quizCatalogImport.howTo')">
      <p>{{ t('helpCenter.quizCatalogImport.step1') }}</p>
      <p>{{ t('helpCenter.quizCatalogImport.step2') }}</p>
      <p>{{ t('helpCenter.quizCatalogImport.step3') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.quizCatalogImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
