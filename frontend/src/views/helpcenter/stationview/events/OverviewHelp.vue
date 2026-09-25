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
  <HelpArticle :title="t('helpCenter.eventsOverview.overviewTitle')" :subtitle="t('helpCenter.eventsOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.eventsOverview.whatIs')">
      <p>{{ t('helpCenter.eventsOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsOverview.pagesTitle')">
      <p>{{ t('helpCenter.eventsOverview.pagesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsOverview.tabsTitle')">
      <p>{{ t('helpCenter.eventsOverview.tabsText') }}</p>
      <p>{{ t('helpCenter.eventsOverview.listsText') }}</p>
      <p>{{ t('helpCenter.eventsOverview.filterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventsOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
