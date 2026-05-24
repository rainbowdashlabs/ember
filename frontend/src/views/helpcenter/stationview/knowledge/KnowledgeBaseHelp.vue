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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.kb.title')" :subtitle="t('helpCenter.kb.subtitle')">
    <HelpSection :title="t('helpCenter.kb.whatIs')">
      <p>{{ t('helpCenter.kb.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.browsingTitle')">
      <p>{{ t('helpCenter.kb.browsingText') }}</p>
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

    <HelpSection :title="t('helpCenter.kb.fileTypesTitle')">
      <ul class="list-disc pl-5 space-y-1 text-sm">
        <li><strong>Markdown</strong>: {{ t('helpCenter.kb.markdownDesc') }}</li>
        <li><strong>PDF</strong>: {{ t('helpCenter.kb.pdfDesc') }}</li>
        <li><strong>{{ t('helpCenter.kb.textFiles') }}</strong>: {{ t('helpCenter.kb.textDesc') }}</li>
        <li><strong>{{ t('helpCenter.kb.images') }}</strong>: {{ t('helpCenter.kb.imagesDesc') }}</li>
        <li><strong>YouTube</strong>: {{ t('helpCenter.kb.youtubeDesc') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editingTitle')">
      <p>{{ t('helpCenter.kb.editingText') }}</p>
    </HelpSection>

    <!-- Dummy: Markdown editor -->
    <NeutralContainer>
      <div class="grid grid-cols-2 gap-3">
        <div class="space-y-1">
          <span class="text-xs text-(--text-muted)">Bearbeiten</span>
          <div class="text-xs font-mono bg-(--bg) p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent whitespace-pre-line"># Überschrift

Text mit **fett** und *kursiv*.

- Punkt 1
- Punkt 2</div>
        </div>
        <div class="space-y-1">
          <span class="text-xs text-(--text-muted)">Vorschau</span>
          <div class="text-xs p-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
            <SubHeader class="font-bold">Überschrift</SubHeader>
            <p>Text mit <strong>fett</strong> und <em>kursiv</em>.</p>
            <ul class="list-disc pl-4"><li>Punkt 1</li><li>Punkt 2</li></ul>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kb.versionsTitle')">
      <p>{{ t('helpCenter.kb.versionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Version list -->
    <NeutralContainer>
      <div class="space-y-2">
        <div class="flex items-center justify-between text-sm">
          <span>Version 3 <SecondaryBadge>aktuell</SecondaryBadge></span>
          <span class="text-xs text-(--text-muted)">vor 2 Stunden</span>
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>Version 2</span>
          <span class="text-xs text-(--text-muted)">vor 1 Tag</span>
        </div>
        <div class="flex items-center justify-between text-sm">
          <span>Version 1</span>
          <span class="text-xs text-(--text-muted)">vor 3 Tagen</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kb.searchTitle')">
      <p>{{ t('helpCenter.kb.searchText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kb.tip') }}</HelpTip>
  </HelpArticle>
</template>
