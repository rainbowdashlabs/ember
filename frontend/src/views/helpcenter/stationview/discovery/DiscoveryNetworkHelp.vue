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
import HelpList from '@/components/helpcenter/HelpList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle
      :title="t('helpCenter.stationDiscoveryNetwork.title')"
      :subtitle="t('helpCenter.stationDiscoveryNetwork.subtitle')"
  >
    <HelpSection :title="t('helpCenter.stationDiscoveryNetwork.whatIs')">
      <p>{{ t('helpCenter.stationDiscoveryNetwork.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationDiscoveryNetwork.filtersTitle')">
      <p>{{ t('helpCenter.stationDiscoveryNetwork.filtersText') }}</p>
      <HelpList :items="[
        t('helpCenter.stationDiscoveryNetwork.filterSearch'),
        t('helpCenter.stationDiscoveryNetwork.filterNearMe'),
        t('helpCenter.stationDiscoveryNetwork.filterRadius'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationDiscoveryNetwork.mapTitle')">
      <p>{{ t('helpCenter.stationDiscoveryNetwork.mapText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.stationDiscoveryNetwork.tip') }}</HelpTip>
  </HelpArticle>
</template>
