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
  <HelpArticle :title="t('helpCenter.formsOverview.overviewTitle')" :subtitle="t('helpCenter.formsOverview.overviewSubtitle')">
    <HelpSection :title="t('helpCenter.formsOverview.whatIs')">
      <p>{{ t('helpCenter.formsOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.formsOverview.pagesTitle')">
      <p>{{ t('helpCenter.formsOverview.pagesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.formsOverview.overviewTip') }}</HelpTip>
  </HelpArticle>
</template>
