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
  <HelpArticle :title="t('helpCenter.profileOverview.overviewTitle')" :subtitle="t('helpCenter.profileOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.profileOverview.whatIs')">
      <p>{{ t('helpCenter.profileOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.profileOverview.pagesTitle')">
      <p>{{ t('helpCenter.profileOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.profileOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
