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
  <HelpArticle :subtitle="t('helpCenter.clusterStorage.subtitle')" :title="t('helpCenter.clusterStorage.title')">
    <HelpSection :title="t('helpCenter.clusterStorage.poolTitle')">
      <p>{{ t('helpCenter.clusterStorage.poolText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorage.handOutTitle')">
      <p>{{ t('helpCenter.clusterStorage.handOutText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorage.defaultsTitle')">
      <p>{{ t('helpCenter.clusterStorage.defaultsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorage.tiersTitle')">
      <p>{{ t('helpCenter.clusterStorage.tiersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterStorage.backendTitle')">
      <p>{{ t('helpCenter.clusterStorage.backendText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterStorage.tip') }}</HelpTip>
  </HelpArticle>
</template>
