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
  <HelpArticle :subtitle="t('helpCenter.clusterModules.subtitle')" :title="t('helpCenter.clusterModules.title')">
    <HelpSection :title="t('helpCenter.clusterModules.whatTitle')">
      <p>{{ t('helpCenter.clusterModules.whatText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterModules.groupsTitle')">
      <p>{{ t('helpCenter.clusterModules.groupsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterModules.effectTitle')">
      <p>{{ t('helpCenter.clusterModules.effectText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterModules.dataTitle')">
      <p>{{ t('helpCenter.clusterModules.dataText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterModules.tip') }}</HelpTip>
  </HelpArticle>
</template>
