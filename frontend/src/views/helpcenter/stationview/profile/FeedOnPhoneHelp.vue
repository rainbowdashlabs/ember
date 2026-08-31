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
  <HelpArticle :title="t('helpCenter.feedPhone.title')" :subtitle="t('helpCenter.feedPhone.subtitle')">
    <HelpSection :title="t('helpCenter.feedPhone.whyTitle')">
      <p>{{ t('helpCenter.feedPhone.whyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.feedPhone.urlTitle')">
      <p>{{ t('helpCenter.feedPhone.urlText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.feedPhone.androidTitle')">
      <p>{{ t('helpCenter.feedPhone.androidIntro') }}</p>
      <NeutralContainer class="space-y-3">
        <ol class="list-decimal list-inside text-sm space-y-1 text-(--text-muted)">
          <li>{{ t('helpCenter.feedPhone.android1') }}</li>
          <li>{{ t('helpCenter.feedPhone.android2') }}</li>
          <li>{{ t('helpCenter.feedPhone.android3') }}</li>
          <li>{{ t('helpCenter.feedPhone.android4') }}</li>
          <li>{{ t('helpCenter.feedPhone.android5') }}</li>
        </ol>
      </NeutralContainer>
      <p>{{ t('helpCenter.feedPhone.androidAfter') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.feedPhone.iosTitle')">
      <p>{{ t('helpCenter.feedPhone.iosIntro') }}</p>
      <NeutralContainer class="space-y-3">
        <ol class="list-decimal list-inside text-sm space-y-1 text-(--text-muted)">
          <li>{{ t('helpCenter.feedPhone.ios1') }}</li>
          <li>{{ t('helpCenter.feedPhone.ios2') }}</li>
          <li>{{ t('helpCenter.feedPhone.ios3') }}</li>
          <li>{{ t('helpCenter.feedPhone.ios4') }}</li>
          <li>{{ t('helpCenter.feedPhone.ios5') }}</li>
        </ol>
      </NeutralContainer>
      <p>{{ t('helpCenter.feedPhone.iosAfter') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.feedPhone.quietTitle')">
      <p>{{ t('helpCenter.feedPhone.quietText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.feedPhone.troubleTitle')">
      <p>{{ t('helpCenter.feedPhone.troubleText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.feedPhone.tip') }}</HelpTip>
  </HelpArticle>
</template>
