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
  <HelpArticle :title="t('helpCenter.manageOverview.overviewTitle')" :subtitle="t('helpCenter.manageOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.manageOverview.whatIs')">
      <p>{{ t('helpCenter.manageOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.manageOverview.pagesTitle')">
      <p>{{ t('helpCenter.manageOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.manageOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
