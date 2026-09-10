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
  <HelpArticle :title="t('helpCenter.documents.title')" :subtitle="t('helpCenter.documents.subtitle')">
    <HelpSection :title="t('helpCenter.documents.whatIs')">
      <p>{{ t('helpCenter.documents.whatIsText') }}</p>
      <p>{{ t('helpCenter.documents.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.rightsTitle')">
      <p>{{ t('helpCenter.documents.rightsRead') }}</p>
      <p>{{ t('helpCenter.documents.rightsSelf') }}</p>
      <p>{{ t('helpCenter.documents.rightsOthers') }}</p>
      <p>{{ t('helpCenter.documents.rightsOwn') }}</p>
      <p>{{ t('helpCenter.documents.rightsBound') }}</p>
      <p>{{ t('helpCenter.documents.rightsHidden') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.unboundTitle')">
      <p>{{ t('helpCenter.documents.unboundText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.moduleTitle')">
      <p>{{ t('helpCenter.documents.moduleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.previewTitle')">
      <p>{{ t('helpCenter.documents.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.searchTitle')">
      <p>{{ t('helpCenter.documents.searchText') }}</p>
      <p>{{ t('helpCenter.documents.tagsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documents.keepTitle')">
      <p>{{ t('helpCenter.documents.keepText') }}</p>
      <p>{{ t('helpCenter.documents.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documents.tip') }}</HelpTip>
  </HelpArticle>
</template>
