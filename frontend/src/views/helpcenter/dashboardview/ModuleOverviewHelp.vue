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
  <HelpArticle :title="t('helpCenter.dashboardOverview.overviewTitle')" :subtitle="t('helpCenter.dashboardOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.dashboardOverview.whatIs')">
      <p>{{ t('helpCenter.dashboardOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.dashboardOverview.pagesTitle')">
      <p>{{ t('helpCenter.dashboardOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.dashboardOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
