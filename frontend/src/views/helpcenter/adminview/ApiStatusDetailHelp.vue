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
  <HelpArticle :title="t('helpCenter.adminApiStatusDetail.title')" :subtitle="t('helpCenter.adminApiStatusDetail.subtitle')">
    <HelpSection :title="t('helpCenter.adminApiStatusDetail.overviewTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.overviewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatusDetail.statusCodesTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.statusCodesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApiStatusDetail.recentTitle')">
      <p>{{ t('helpCenter.adminApiStatusDetail.recentText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApiStatusDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
