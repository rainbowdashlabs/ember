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
  <HelpArticle :title="t('helpCenter.attendanceOverview.overviewTitle')" :subtitle="t('helpCenter.attendanceOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.attendanceOverview.whatIs')">
      <p>{{ t('helpCenter.attendanceOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceOverview.pagesTitle')">
      <p>{{ t('helpCenter.attendanceOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
