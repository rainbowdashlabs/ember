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
  <HelpArticle :title="t('helpCenter.adminOverview.title')" :subtitle="t('helpCenter.adminOverview.subtitle')">
    <HelpSection :title="t('helpCenter.adminOverview.whatIs')">
      <p>{{ t('helpCenter.adminOverview.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminOverview.whoCanSee')">
      <p>{{ t('helpCenter.adminOverview.whoCanSeeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminOverview.tip') }}</HelpTip>
  </HelpArticle>
</template>
