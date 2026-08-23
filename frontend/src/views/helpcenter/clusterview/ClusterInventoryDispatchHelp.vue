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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryDispatch.subtitle')"
               :title="t('helpCenter.clusterInventoryDispatch.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryDispatch.whatTitle')">
      <p>{{ t('helpCenter.clusterInventoryDispatch.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryDispatch.pickTitle')">
      <p>{{ t('helpCenter.clusterInventoryDispatch.pickText') }}</p>
      <p>{{ t('helpCenter.clusterInventoryDispatch.storeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryDispatch.chainTitle')">
      <p>{{ t('helpCenter.clusterInventoryDispatch.chainText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterInventoryDispatch.tip') }}</HelpTip>
  </HelpArticle>
</template>
