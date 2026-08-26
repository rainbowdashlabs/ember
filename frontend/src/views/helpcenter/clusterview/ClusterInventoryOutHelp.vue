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
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryOut.subtitle')"
               :title="t('helpCenter.clusterInventoryOut.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryOut.whatTitle')">
      <p>{{ t('helpCenter.clusterInventoryOut.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryOut.custodyTitle')">
      <p>{{ t('helpCenter.clusterInventoryOut.custodyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterInventoryOut.tip') }}</HelpTip>
  </HelpArticle>
</template>
