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
  <HelpArticle :title="t('helpCenter.calendarFeeds.title')" :subtitle="t('helpCenter.calendarFeeds.subtitle')">
    <HelpSection :title="t('helpCenter.calendarFeeds.whatIs')">
      <p>{{ t('helpCenter.calendarFeeds.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.calendarFeeds.levelsTitle')">
      <p>{{ t('helpCenter.calendarFeeds.levelsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.calendarFeeds.categoriesTitle')">
      <p>{{ t('helpCenter.calendarFeeds.categoriesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.calendarFeeds.addressTitle')">
      <p>{{ t('helpCenter.calendarFeeds.addressText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.calendarFeeds.whoTitle')">
      <p>{{ t('helpCenter.calendarFeeds.whoText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.calendarFeeds.tip') }}</HelpTip>
  </HelpArticle>
</template>
