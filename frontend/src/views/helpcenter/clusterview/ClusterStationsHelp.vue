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
  <HelpArticle :subtitle="t('helpCenter.clusterStations.subtitle')" :title="t('helpCenter.clusterStations.title')">
    <HelpSection :title="t('helpCenter.clusterStations.twoWaysTitle')">
      <p>{{ t('helpCenter.clusterStations.twoWaysText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStations.createTitle')">
      <p>{{ t('helpCenter.clusterStations.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStations.releaseTitle')">
      <p>{{ t('helpCenter.clusterStations.releaseText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterStations.tip') }}</HelpTip>
  </HelpArticle>
</template>
