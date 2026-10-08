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
import DateTokenTable from './templateeditorhelp/DateTokenTable.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.documentTemplateEditor.title')" :subtitle="t('helpCenter.documentTemplateEditor.subtitle')">
    <HelpSection :title="t('helpCenter.documentTemplateEditor.generalTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.generalText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.legalText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.issuerTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.issuerText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.issuerMissingText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.issuerAssociationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.letterheadTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.letterheadText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.pageText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.bodyTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.bodyText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.placeholdersText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.fontText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.alignText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.importText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.datesTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.datesText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.datesOwnText') }}</p>
      <DateTokenTable/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.signatureTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.signatureText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.signatureGuardiansText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.signatureTwiceText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.signatureStatementText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.fillInText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.signatureRequestText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.signatureKeepText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.pdfTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.pdfText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.pdfFieldsText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.pdfFormText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.pdfVersionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.pronounsTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.pronounsText') }}</p>
      <p>{{ t('helpCenter.documentTemplateEditor.pronounsNameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.selfServiceTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.selfServiceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.documentTemplateEditor.previewTitle')">
      <p>{{ t('helpCenter.documentTemplateEditor.previewText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.documentTemplateEditor.tip') }}</HelpTip>
  </HelpArticle>
</template>
