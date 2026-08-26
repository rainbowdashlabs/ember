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
  <HelpArticle :subtitle="t('helpCenter.clusterSettings.subtitle')" :title="t('helpCenter.clusterSettings.title')">
    <HelpSection :title="t('helpCenter.clusterSettings.nameTitle')">
      <p>{{ t('helpCenter.clusterSettings.nameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterSettings.descriptionTitle')">
      <p>{{ t('helpCenter.clusterSettings.descriptionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterSettings.whoTitle')">
      <p>{{ t('helpCenter.clusterSettings.whoText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterSettings.tip') }}</HelpTip>
  </HelpArticle>
</template>
