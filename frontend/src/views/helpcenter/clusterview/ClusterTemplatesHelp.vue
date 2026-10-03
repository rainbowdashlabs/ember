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
  <HelpArticle :title="t('helpCenter.clusterDocumentTemplates.title')" :subtitle="t('helpCenter.clusterDocumentTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.whatIs')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.whatIsText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentTemplates.stationsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.placeholdersTitle')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.placeholdersText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentTemplates.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.selfServiceTitle')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.selfServiceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.filesTitle')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.picturesText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentTemplates.fontsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.listTitle')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.listText') }}</p>
      <p>{{ t('helpCenter.clusterDocumentTemplates.duplicateText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.clusterDocumentTemplates.rightsTitle')">
      <p>{{ t('helpCenter.clusterDocumentTemplates.rightsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.clusterDocumentTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
