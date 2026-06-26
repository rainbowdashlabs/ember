/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import BulletList from '@/components/typography/BulletList.vue'
import CreateModalDummy from './CreateModalDummy.vue'
import EditorMetadataDummy from './EditorMetadataDummy.vue'
import LayoutRowDummy from './LayoutRowDummy.vue'

const {t} = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.pages.createTitle')">
    <p>{{ t('helpCenter.pages.createText') }}</p>
    <p>{{ t('helpCenter.pages.createText2') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.createModalTitle')">
    <CreateModalDummy/>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.editorTitle')">
    <p>{{ t('helpCenter.pages.editorText') }}</p>
    <p>{{ t('helpCenter.pages.editorText2') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.metadataTitle')">
    <p>{{ t('helpCenter.pages.metadataText') }}</p>
    <BulletList>
      <li><strong>{{ t('stationPages.editor.titleLabel') }}</strong>: {{ t('helpCenter.pages.metaFieldTitle') }}</li>
      <li><strong>{{ t('stationPages.editor.slugLabel') }}</strong>: {{ t('helpCenter.pages.metaFieldSlug') }}</li>
      <li><strong>{{ t('stationPages.editor.parent') }}</strong>: {{ t('helpCenter.pages.metaFieldParent') }}</li>
      <li><strong>{{ t('stationPages.editor.metaDescription') }}</strong>: {{ t('helpCenter.pages.metaFieldDescription') }}</li>
    </BulletList>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.editorDummyTitle')">
    <EditorMetadataDummy/>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.layoutTitle')">
    <p>{{ t('helpCenter.pages.layoutText') }}</p>
    <p>{{ t('helpCenter.pages.layoutText2') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.layoutDummyTitle')">
    <LayoutRowDummy/>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.contentTypesTitle')">
    <p>{{ t('helpCenter.pages.contentTypesText') }}</p>
    <BulletList>
      <li><strong>{{ t('stationPages.contentType.markdown') }}</strong>: {{ t('helpCenter.pages.contentTypeMarkdown') }}</li>
      <li><strong>{{ t('stationPages.contentType.image') }}</strong>: {{ t('helpCenter.pages.contentTypeImage') }}</li>
      <li><strong>{{ t('stationPages.contentType.video') }}</strong>: {{ t('helpCenter.pages.contentTypeVideo') }}</li>
    </BulletList>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.previewTitle')">
    <p>{{ t('helpCenter.pages.previewText') }}</p>
  </HelpSection>

  <HelpSection :title="t('helpCenter.pages.saveTitle')">
    <p>{{ t('helpCenter.pages.saveText') }}</p>
  </HelpSection>
</template>
