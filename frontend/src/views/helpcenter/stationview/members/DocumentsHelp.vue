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
  <HelpArticle :title="t('helpCenter.memberDocuments.title')" :subtitle="t('helpCenter.memberDocuments.subtitle')">
    <HelpSection :title="t('helpCenter.memberDocuments.whatIs')">
      <p>{{ t('helpCenter.memberDocuments.whatIsText') }}</p>
      <p>{{ t('helpCenter.memberDocuments.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.memberDocuments.rightsTitle')">
      <p>{{ t('helpCenter.memberDocuments.rightsRead') }}</p>
      <p>{{ t('helpCenter.memberDocuments.rightsSelf') }}</p>
      <p>{{ t('helpCenter.memberDocuments.rightsOthers') }}</p>
      <p>{{ t('helpCenter.memberDocuments.rightsHidden') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.memberDocuments.previewTitle')">
      <p>{{ t('helpCenter.memberDocuments.previewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.memberDocuments.searchTitle')">
      <p>{{ t('helpCenter.memberDocuments.searchText') }}</p>
      <p>{{ t('helpCenter.memberDocuments.tagsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.memberDocuments.keepTitle')">
      <p>{{ t('helpCenter.memberDocuments.keepText') }}</p>
      <p>{{ t('helpCenter.memberDocuments.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.memberDocuments.tip') }}</HelpTip>
  </HelpArticle>
</template>
