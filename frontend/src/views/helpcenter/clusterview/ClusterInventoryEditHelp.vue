/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :subtitle="t('helpCenter.clusterInventoryEdit.subtitle')" :title="t('helpCenter.clusterInventoryEdit.title')">
    <HelpSection :title="t('helpCenter.clusterInventoryEdit.oneTitle')">
      <p>{{ t('helpCenter.clusterInventoryEdit.oneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterInventoryEdit.twoTitle')">
      <p>{{ t('helpCenter.clusterInventoryEdit.twoText') }}</p>
    </HelpSection>
  </HelpArticle>
</template>
