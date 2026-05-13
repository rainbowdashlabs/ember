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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.newsEdit.title')" :subtitle="t('helpCenter.newsEdit.subtitle')">
    <HelpSection :title="t('helpCenter.newsEdit.whatShown')">
      <p>{{ t('helpCenter.newsEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsEdit.titleField')">
      <p>{{ t('helpCenter.newsEdit.titleFieldText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsEdit.contentTitle')">
      <p>{{ t('helpCenter.newsEdit.contentText') }}</p>
    </HelpSection>

    <!-- Dummy: Editor -->
    <NeutralContainer class="space-y-4">
      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('news.titleField') }}</label>
        <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
          Dienstplanänderung Mai
        </div>
      </div>
      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <label class="block text-sm font-medium">{{ t('news.content') }}</label>
          <button type="button" class="text-xs text-primary hover:underline">
            {{ t('news.showPreview') }}
          </button>
        </div>
        <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm min-h-[120px]">
          Ab dem 20. Mai gilt ein **neuer Dienstplan**. Bitte prüft eure Einteilungen.

          ## Änderungen
          - Dienstag: Beginn um 18:30 statt 18:00
          - Freitag: entfällt bis auf Weiteres
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('news.markdownHint') }}</p>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.newsEdit.previewTitle')">
      <p>{{ t('helpCenter.newsEdit.previewText') }}</p>
    </HelpSection>

    <!-- Dummy: Preview -->
    <NeutralContainer class="space-y-2">
      <SubHeader>{{ t('news.preview') }}</SubHeader>
      <NeutralContainer class="prose prose-sm dark:prose-invert max-w-none">
        <p>Ab dem 20. Mai gilt ein <strong>neuer Dienstplan</strong>. Bitte prüft eure Einteilungen.</p>
        <h2>Änderungen</h2>
        <ul>
          <li>Dienstag: Beginn um 18:30 statt 18:00</li>
          <li>Freitag: entfällt bis auf Weiteres</li>
        </ul>
      </NeutralContainer>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.newsEdit.visibilityTitle')">
      <p>{{ t('helpCenter.newsEdit.visibilityText') }}</p>
    </HelpSection>

    <!-- Dummy: Visibility groups -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('news.restrictToGroups') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('news.restrictHint') }}</p>
      <div class="flex flex-wrap gap-2">
        <span class="rounded-lg px-3 py-1.5 text-xs font-medium border border-primary bg-primary/10 text-primary ring-1 ring-primary/30">
          Betreuer
        </span>
        <span class="rounded-lg px-3 py-1.5 text-xs font-medium border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">
          Anfänger
        </span>
        <span class="rounded-lg px-3 py-1.5 text-xs font-medium border border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted)">
          Fortgeschritten
        </span>
      </div>
    </NeutralContainer>

    <!-- Dummy: Save buttons -->
    <div class="flex justify-end gap-3">
      <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
    </div>

    <HelpTip>{{ t('helpCenter.newsEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
