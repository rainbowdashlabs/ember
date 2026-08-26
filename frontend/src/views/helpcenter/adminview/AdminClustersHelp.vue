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
  <HelpArticle :subtitle="t('helpCenter.adminClusters.subtitle')" :title="t('helpCenter.adminClusters.title')">
    <HelpSection :title="t('helpCenter.adminClusters.whatTitle')">
      <p>{{ t('helpCenter.adminClusters.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminClusters.createTitle')">
      <p>{{ t('helpCenter.adminClusters.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminClusters.deleteTitle')">
      <p>{{ t('helpCenter.adminClusters.deleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminClusters.tip') }}</HelpTip>
  </HelpArticle>
</template>
