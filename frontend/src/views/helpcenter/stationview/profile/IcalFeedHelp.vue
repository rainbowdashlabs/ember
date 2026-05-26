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
  <HelpArticle :title="t('helpCenter.icalFeed.title')" :subtitle="t('helpCenter.icalFeed.subtitle')">
    <HelpSection :title="t('helpCenter.icalFeed.whatIs')">
      <p>{{ t('helpCenter.icalFeed.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.tokenTitle')">
      <p>{{ t('helpCenter.icalFeed.tokenText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <p class="text-sm font-medium">{{ t('helpCenter.icalFeed.stepsTitle') }}</p>
      <ol class="list-decimal list-inside text-sm space-y-1 text-(--text-muted)">
        <li>{{ t('helpCenter.icalFeed.step1') }}</li>
        <li>{{ t('helpCenter.icalFeed.step2') }}</li>
        <li>{{ t('helpCenter.icalFeed.step3') }}</li>
      </ol>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.icalFeed.googleTitle')">
      <p>{{ t('helpCenter.icalFeed.googleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.appleTitle')">
      <p>{{ t('helpCenter.icalFeed.appleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.iosTitle')">
      <p>{{ t('helpCenter.icalFeed.iosText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.androidTitle')">
      <p>{{ t('helpCenter.icalFeed.androidText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.outlookTitle')">
      <p>{{ t('helpCenter.icalFeed.outlookText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.icalFeed.thunderbirdTitle')">
      <p>{{ t('helpCenter.icalFeed.thunderbirdText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.icalFeed.tip') }}</HelpTip>
  </HelpArticle>
</template>
