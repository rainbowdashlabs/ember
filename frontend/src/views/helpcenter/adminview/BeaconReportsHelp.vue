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
  <HelpArticle :title="t('helpCenter.adminBeaconReports.title')"
               :subtitle="t('helpCenter.adminBeaconReports.subtitle')">
    <HelpSection :title="t('helpCenter.adminBeaconReports.whatIs')">
      <p>{{ t('helpCenter.adminBeaconReports.whatIsText') }}</p>
      <p>{{ t('helpCenter.adminBeaconReports.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminBeaconReports.readingTitle')">
      <p>{{ t('helpCenter.adminBeaconReports.readingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminBeaconReports.tip') }}</HelpTip>
  </HelpArticle>
</template>
