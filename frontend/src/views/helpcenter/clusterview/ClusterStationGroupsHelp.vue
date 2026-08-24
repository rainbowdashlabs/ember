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
  <HelpArticle :subtitle="t('helpCenter.clusterStationGroups.subtitle')"
               :title="t('helpCenter.clusterStationGroups.title')">
    <HelpSection :title="t('helpCenter.clusterStationGroups.whatTitle')">
      <p>{{ t('helpCenter.clusterStationGroups.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStationGroups.howToTitle')">
      <p>{{ t('helpCenter.clusterStationGroups.howToText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStationGroups.overlapTitle')">
      <p>{{ t('helpCenter.clusterStationGroups.overlapText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterStationGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
