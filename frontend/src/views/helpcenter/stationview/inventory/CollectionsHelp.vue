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
  <HelpArticle
      :title="t('helpCenter.inventoryCollections.title')"
      :subtitle="t('helpCenter.inventoryCollections.subtitle')"
  >
    <HelpSection :title="t('helpCenter.inventoryCollections.whatIs')">
      <p>{{ t('helpCenter.inventoryCollections.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.templateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCollections.linesTitle')">
      <p>{{ t('helpCenter.inventoryCollections.linesItem') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.linesArt') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.linesCount') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.linesPick') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCollections.windowTitle')">
      <p>{{ t('helpCenter.inventoryCollections.windowText') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.clusterText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCollections.againstTagsTitle')">
      <p>{{ t('helpCenter.inventoryCollections.againstTagsText') }}</p>
      <p>{{ t('helpCenter.inventoryCollections.artGoneText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryCollections.tip') }}</HelpTip>
  </HelpArticle>
</template>
