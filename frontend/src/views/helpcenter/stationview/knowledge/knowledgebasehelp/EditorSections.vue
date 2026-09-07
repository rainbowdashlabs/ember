/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import BulletList from '@/components/typography/BulletList.vue'
import DummyVersionList from './DummyVersionList.vue'

/**
 * The half of the wiki article that only somebody who may write in it needs to read: what can be
 * made, changed, moved, marked, deleted and read back out of the trash.
 */
const {t} = useI18n()
</script>

<template>
  <div>
    <HelpSection :title="t('helpCenter.kb.createMenuTitle')">
      <p>{{ t('helpCenter.kb.createMenuText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.kb.createFolder') }}</li>
        <li>{{ t('helpCenter.kb.createMarkdown') }}</li>
        <li>{{ t('helpCenter.kb.createUpload') }}</li>
        <li>{{ t('helpCenter.kb.createYoutube') }}</li>
        <li>{{ t('helpCenter.kb.createLink') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editingTitle')">
      <p>{{ t('helpCenter.kb.editingTextFixed') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.moveTitle')">
      <p>{{ t('helpCenter.kb.moveText') }}</p>
      <p>{{ t('helpCenter.kb.moveRightsText') }}</p>
      <p>{{ t('helpCenter.kb.movePreviewText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.selectionTitle')">
      <p>{{ t('helpCenter.kb.selectionText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.kb.selectionMove') }}</li>
        <li>{{ t('helpCenter.kb.selectionTags') }}</li>
        <li>{{ t('helpCenter.kb.selectionDelete') }}</li>
      </BulletList>
      <p>{{ t('helpCenter.kb.selectionPartialText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.trashTitle')">
      <p>{{ t('helpCenter.kb.trashText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.kb.trashWhoSees') }}</li>
        <li>{{ t('helpCenter.kb.trashFolders') }}</li>
        <li>{{ t('helpCenter.kb.trashStorage') }}</li>
      </BulletList>
      <p>{{ t('helpCenter.kb.trashRetentionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.versionsTitle')">
      <p>{{ t('helpCenter.kb.versionsText') }}</p>
    </HelpSection>

    <DummyVersionList/>
  </div>
</template>
