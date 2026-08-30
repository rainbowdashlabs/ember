/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpList from '@/components/helpcenter/HelpList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.stationFeeds.title')" :subtitle="t('helpCenter.stationFeeds.subtitle')">
    <HelpSection :title="t('helpCenter.stationFeeds.whatIs')">
      <p>{{ t('helpCenter.stationFeeds.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationFeeds.columnsTitle')">
      <p>{{ t('helpCenter.stationFeeds.columnsText') }}</p>
      <HelpList :items="[
        t('helpCenter.stationFeeds.columnMember'),
        t('helpCenter.stationFeeds.columnSince'),
        t('helpCenter.stationFeeds.columnCalendar'),
        t('helpCenter.stationFeeds.columnNotifications'),
        t('helpCenter.stationFeeds.columnState'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationFeeds.dormantTitle')">
      <p>{{ t('helpCenter.stationFeeds.dormantText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationFeeds.privacyTitle')">
      <p>{{ t('helpCenter.stationFeeds.privacyText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
