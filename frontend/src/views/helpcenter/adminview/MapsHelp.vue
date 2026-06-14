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
  <HelpArticle :title="t('helpCenter.adminMaps.title')" :subtitle="t('helpCenter.adminMaps.subtitle')">
    <HelpSection :title="t('helpCenter.adminMaps.whatIs')">
      <p>{{ t('helpCenter.adminMaps.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMaps.providersTitle')">
      <p>{{ t('helpCenter.adminMaps.providersText') }}</p>
      <HelpList :items="[
        t('helpCenter.adminMaps.providerOsm'),
        t('helpCenter.adminMaps.providerStadia'),
        t('helpCenter.adminMaps.providerCommercial'),
        t('helpCenter.adminMaps.providerCustom'),
      ]"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminMaps.cacheTitle')">
      <p>{{ t('helpCenter.adminMaps.cacheText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminMaps.tip') }}</HelpTip>
  </HelpArticle>
</template>
