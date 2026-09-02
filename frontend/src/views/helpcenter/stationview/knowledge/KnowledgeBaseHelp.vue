/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import BulletList from '@/components/typography/BulletList.vue'
import EditorSections from '@/views/helpcenter/stationview/knowledge/knowledgebasehelp/EditorSections.vue'
import {StationPermission} from '@/api/types'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.kb.title')" :subtitle="t('helpCenter.kb.subtitle')">
    <HelpSection :title="t('helpCenter.kb.whatIs')">
      <p>{{ t('helpCenter.kb.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.browsingTitle')">
      <p>{{ t('helpCenter.kb.browsingText') }}</p>
      <p>{{ t('helpCenter.kb.breadcrumbText') }}</p>
      <p>{{ t('helpCenter.kb.viewToggleText') }}</p>
    </HelpSection>

    <!-- Dummy: Tile browser -->
    <NeutralContainer>
      <div class="grid grid-cols-3 gap-3">
        <div class="flex flex-col items-center gap-1 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'folder']" class="text-2xl text-primary" />
          <span class="text-xs font-medium text-center">Grundlagen</span>
          <span class="text-xs text-(--text-muted)">Basiswissen</span>
        </div>
        <div class="flex flex-col items-center gap-1 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'folder']" class="text-2xl text-primary" />
          <span class="text-xs font-medium text-center">Ausrüstung</span>
          <span class="text-xs text-(--text-muted)">PSA & Geräte</span>
        </div>
        <div class="flex flex-col items-center gap-1 p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
          <font-awesome-icon :icon="['fas', 'file-lines']" class="text-2xl text-(--text-muted)" />
          <span class="text-xs font-medium text-center">Willkommen</span>
          <span class="text-xs text-(--text-muted)">Startseite</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kb.actionsMenuTitle')">
      <p>{{ t('helpCenter.kb.actionsMenuText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.visibilityTitle')">
      <p>{{ t('helpCenter.kb.visibilityText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.eyesTitle')">
      <p>{{ t('helpCenter.kb.eyesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.fileTypesTitle')">
      <BulletList>
        <li><strong>Markdown</strong>: {{ t('helpCenter.kb.markdownDesc') }}</li>
        <li><strong>PDF</strong>: {{ t('helpCenter.kb.pdfDesc') }}</li>
        <li><strong>{{ t('helpCenter.kb.textFiles') }}</strong>: {{ t('helpCenter.kb.textDesc') }}</li>
        <li><strong>{{ t('helpCenter.kb.images') }}</strong>: {{ t('helpCenter.kb.imagesDesc') }}</li>
        <li><strong>YouTube</strong>: {{ t('helpCenter.kb.youtubeDesc') }}</li>
        <li><strong>{{ t('helpCenter.kb.presentations') }}</strong>: {{ t('helpCenter.kb.presentationsDesc') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.favouritesTitle')">
      <p>{{ t('helpCenter.kb.favouritesText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.KNOWLEDGE_EDIT]" :label="t('helpCenter.permissionLabel.knowledgeEdit')">
      <EditorSections/>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.kb.searchTitle')">
      <p>{{ t('helpCenter.kb.searchText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kb.tip') }}</HelpTip>
  </HelpArticle>
</template>
