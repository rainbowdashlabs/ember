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
  <HelpArticle :title="t('helpCenter.adminDiscovery.title')" :subtitle="t('helpCenter.adminDiscovery.subtitle')">
    <HelpSection :title="t('helpCenter.adminDiscovery.whatIs')">
      <p>{{ t('helpCenter.adminDiscovery.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDiscovery.peersTitle')">
      <p>{{ t('helpCenter.adminDiscovery.peersText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminDiscovery.peersSourceBootstrap'),
        t('helpCenter.adminDiscovery.peersSourceGossip'),
        t('helpCenter.adminDiscovery.peersSourceManual'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDiscovery.settingsTitle')">
      <p>{{ t('helpCenter.adminDiscovery.settingsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminDiscovery.blocklistTitle')">
      <p>{{ t('helpCenter.adminDiscovery.blocklistText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminDiscovery.tip') }}</HelpTip>
  </HelpArticle>
</template>
