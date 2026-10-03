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
  <HelpArticle :title="t('helpCenter.documentTemplates.title')" :subtitle="t('helpCenter.documentTemplates.subtitle')">
    <HelpSection :title="t('helpCenter.documentTemplates.whatIs')">
      <p>{{ t('helpCenter.documentTemplates.whatIsText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.whatIsPdfText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.generateTitle')">
      <p>{{ t('helpCenter.documentTemplates.generateText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.missingText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.issuerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.bulkTitle')">
      <p>{{ t('helpCenter.documentTemplates.bulkText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.bulkPreviewText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.bulkRunText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.forAppointmentsTitle')">
      <p>{{ t('helpCenter.documentTemplates.forAppointmentsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.selfServiceTitle')">
      <p>{{ t('helpCenter.documentTemplates.selfServiceText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.cooldownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.associationTitle')">
      <p>{{ t('helpCenter.documentTemplates.associationText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.associationSelfServiceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.listTitle')">
      <p>{{ t('helpCenter.documentTemplates.listText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.listPickerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.duplicateTitle')">
      <p>{{ t('helpCenter.documentTemplates.duplicateText') }}</p>
      <p>{{ t('helpCenter.documentTemplates.duplicateAssociationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.archiveTitle')">
      <p>{{ t('helpCenter.documentTemplates.archiveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplates.rightsTitle')">
      <p>{{ t('helpCenter.documentTemplates.rightsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documentTemplates.tip') }}</HelpTip>
  </HelpArticle>
</template>
