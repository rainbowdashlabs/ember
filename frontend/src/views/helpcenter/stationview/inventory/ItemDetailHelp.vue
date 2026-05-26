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
  <HelpArticle :title="t('helpCenter.itemDetail.title')" :subtitle="t('helpCenter.itemDetail.subtitle')">
    <HelpSection :title="t('helpCenter.itemDetail.whatIs')">
      <p>{{ t('helpCenter.itemDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.itemDetail.historyTitle')">
      <p>{{ t('helpCenter.itemDetail.historyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.itemDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
