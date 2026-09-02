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

const {t} = useI18n()
</script>

<template>
  <HelpArticle
      :title="t('helpCenter.lendingCollect.title')"
      :subtitle="t('helpCenter.lendingCollect.subtitle')"
  >
    <HelpSection :title="t('helpCenter.lendingCollect.whatIs')">
      <p>{{ t('helpCenter.lendingCollect.whatIsText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.seriesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingCollect.linesTitle')">
      <p>{{ t('helpCenter.lendingCollect.linesText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.pickText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.tooManyText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.leadText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingCollect.coverTitle')">
      <p>{{ t('helpCenter.lendingCollect.coverText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.overClaimText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lendingCollect.borrowTitle')">
      <p>{{ t('helpCenter.lendingCollect.borrowText') }}</p>
      <p>{{ t('helpCenter.lendingCollect.sendText') }}</p>
      <HelpTip>{{ t('helpCenter.lendingCollect.tip') }}</HelpTip>
    </HelpSection>
  </HelpArticle>
</template>
