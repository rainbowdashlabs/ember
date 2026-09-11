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
  <HelpArticle :title="t('helpCenter.adminBeaconMetrics.title')"
               :subtitle="t('helpCenter.adminBeaconMetrics.subtitle')">
    <HelpSection :title="t('helpCenter.adminBeaconMetrics.whatIs')">
      <p>{{ t('helpCenter.adminBeaconMetrics.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminBeaconMetrics.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeaconMetrics.readingTitle')">
      <p>{{ t('helpCenter.adminBeaconMetrics.readingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminBeaconMetrics.tip') }}</HelpTip>
  </HelpArticle>
</template>
