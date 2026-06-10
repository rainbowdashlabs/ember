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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import BulletList from '@/components/typography/BulletList.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.pageEditor.title')" :subtitle="t('helpCenter.pageEditor.subtitle')">
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
      <NeutralContainer class="space-y-3">
        <span class="font-semibold">{{ t('stationPages.editor.metadata') }}</span>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <FormLabel>{{ t('stationPages.editor.titleLabel') }}</FormLabel>
            <TextInput :model-value="t('helpCenter.pages.dummyPage1')" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
          </div>
          <div>
            <FormLabel>{{ t('stationPages.editor.slugLabel') }}</FormLabel>
            <TextInput :model-value="t('helpCenter.pages.dummySlug1')" :placeholder="t('stationPages.editor.slugPlaceholder')"/>
          </div>
          <div>
            <FormLabel>{{ t('stationPages.editor.parent') }}</FormLabel>
            <SelectInput :model-value="''">
              <option value="">{{ t('stationPages.editor.noParent') }}</option>
            </SelectInput>
          </div>
          <div>
            <FormLabel>{{ t('stationPages.editor.metaDescription') }}</FormLabel>
            <TextInput :model-value="t('helpCenter.pages.dummyMetaDesc')" :placeholder="t('stationPages.editor.metaDescriptionPlaceholder')"/>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.layoutTitle')">
      <p>{{ t('helpCenter.pages.layoutText') }}</p>
      <p>{{ t('helpCenter.pages.layoutText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.layoutDummyTitle')">
      <NeutralContainer class="space-y-2">
        <NeutralContainer class="flex gap-2">
          <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
            {{ t('stationPages.contentType.markdown') }}
          </div>
          <div class="flex-1 border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
            {{ t('stationPages.contentType.image') }}
          </div>
        </NeutralContainer>
        <div class="flex items-center justify-center gap-2 py-1">
          <div class="flex-1 h-px bg-[var(--border)]"/>
          <IconButton :icon="['fas', 'plus']" :label="t('stationPages.editor.addRowHere')"/>
          <div class="flex-1 h-px bg-[var(--border)]"/>
        </div>
        <NeutralContainer class="border border-dashed border-[var(--border)] rounded p-3 text-center text-sm text-[var(--text-muted)]">
          {{ t('stationPages.contentType.video') }}
        </NeutralContainer>
      </NeutralContainer>
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

    <HelpPermissionGuard :permissions="[StationPermission.PAGE_MANAGER]" :label="t('helpCenter.permissionLabel.pageManager')">
      <HelpSection :title="t('helpCenter.pages.managerTitle')">
        <p>{{ t('helpCenter.pages.managerText') }}</p>
        <BulletList>
          <li>{{ t('helpCenter.pages.managerPublish') }}</li>
          <li>{{ t('helpCenter.pages.managerLanding') }}</li>
          <li>{{ t('helpCenter.pages.managerDelete') }}</li>
        </BulletList>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.pages.tip') }}</HelpTip>
  </HelpArticle>
</template>
