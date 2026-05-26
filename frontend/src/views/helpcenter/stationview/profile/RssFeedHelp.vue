/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.rssFeed.title')" :subtitle="t('helpCenter.rssFeed.subtitle')">
    <HelpSection :title="t('helpCenter.rssFeed.whatIs')">
      <p>{{ t('helpCenter.rssFeed.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.rssFeed.tokenTitle')">
      <p>{{ t('helpCenter.rssFeed.tokenText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <p class="text-sm font-medium">{{ t('helpCenter.rssFeed.stepsTitle') }}</p>
      <ol class="list-decimal list-inside text-sm space-y-1 text-(--text-muted)">
        <li>{{ t('helpCenter.rssFeed.step1') }}</li>
        <li>{{ t('helpCenter.rssFeed.step2') }}</li>
        <li>{{ t('helpCenter.rssFeed.step3') }}</li>
      </ol>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.rssFeed.thunderbirdTitle')">
      <p>{{ t('helpCenter.rssFeed.thunderbirdText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.rssFeed.outlookTitle')">
      <p>{{ t('helpCenter.rssFeed.outlookText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.rssFeed.androidTitle')">
      <p>{{ t('helpCenter.rssFeed.androidText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.rssFeed.iosTitle')">
      <p>{{ t('helpCenter.rssFeed.iosText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.rssFeed.webTitle')">
      <p>{{ t('helpCenter.rssFeed.webText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.rssFeed.tip') }}</HelpTip>
  </HelpArticle>
</template>
