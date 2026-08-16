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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import BulletList from '@/components/typography/BulletList.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.kbFileView.title')" :subtitle="t('helpCenter.kbFileView.subtitle')">
    <HelpSection :title="t('helpCenter.kbFileView.whatIs')">
      <p>{{ t('helpCenter.kbFileView.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.typesTitle')">
      <p>{{ t('helpCenter.kbFileView.typesText') }}</p>
    </HelpSection>

    <!-- Dummy: file header -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-start justify-between gap-3">
        <div>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'file-lines']" class="text-[var(--accent)]" />
            <span class="font-semibold text-base">Grundlagen der Ersten Hilfe</span>
            <PrimaryBadge>Markdown</PrimaryBadge>
          </div>
          <MutedText tag="div" class="mt-1">Zuletzt geändert: 12. Mai 2026 · Max Mustermann</MutedText>
        </div>
        <div class="flex gap-2 flex-shrink-0">
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'pen']" class="mr-1" /> Bearbeiten
          </SecondaryButton>
          <SecondaryButton compact disabled>
            <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1" /> Versionen
          </SecondaryButton>
        </div>
      </div>
      <div class="border-t border-[var(--border)] pt-3 text-sm space-y-2">
        <p class="font-semibold text-base">Was ist Erste Hilfe?</p>
        <p class="text-sm">Erste Hilfe bedeutet, einer verletzten oder erkrankten Person sofort zu helfen — noch bevor der Rettungsdienst eintrifft. Das kann Leben retten.</p>
        <BulletList>
          <li>Notruf absetzen (112)</li>
          <li>Stabile Seitenlage bei Bewusstlosigkeit</li>
          <li>Herzdruckmassage bei Herzstillstand</li>
        </BulletList>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kbFileView.fileTypesDetailTitle')">
      <p>{{ t('helpCenter.kbFileView.fileTypesDetailText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.tagsTitle')">
      <p>{{ t('helpCenter.kbFileView.tagsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.relatedTitle')">
      <p>{{ t('helpCenter.kbFileView.relatedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.commentsTitle')">
      <p>{{ t('helpCenter.kbFileView.commentsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.editTitle')">
      <p>{{ t('helpCenter.kbFileView.editText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.shareLinkTitle')">
      <p>{{ t('helpCenter.kbFileView.shareLinkText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.versionsTitle')">
      <p>{{ t('helpCenter.kbFileView.versionsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.pdfExportTitle')">
      <p>{{ t('helpCenter.kbFileView.pdfExportText') }}</p>
      <p>{{ t('helpCenter.kbFileView.pdfExportFormatting') }}</p>
      <p>{{ t('helpCenter.kbFileView.pdfExportWhere') }}</p>
      <NeutralContainer>
        <SecondaryButton compact disabled>
          <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1"/> {{ t('kb.downloadPdf') }}
        </SecondaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kbFileView.presentationsTitle')">
      <p>{{ t('helpCenter.kbFileView.presentationsText') }}</p>
      <p>{{ t('helpCenter.kbFileView.presentationsFormats') }}</p>
    </HelpSection>

    <!-- Dummy: Presentation conversion states -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'file-powerpoint']" class="text-[var(--accent)]"/>
        <span class="font-semibold">Schulung-Brandschutz.pptx</span>
        <PrimaryBadge>Präsentation</PrimaryBadge>
      </div>
      <div class="flex gap-2">
        <SecondaryButton compact disabled>
          <font-awesome-icon :icon="['fas', 'display']" class="mr-1"/> Präsentieren
        </SecondaryButton>
        <SecondaryButton compact disabled>
          <font-awesome-icon :icon="['fas', 'download']" class="mr-1"/> Original herunterladen
        </SecondaryButton>
      </div>
      <MutedText tag="p">{{ t('helpCenter.kbFileView.presentationsButtons') }}</MutedText>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kbFileView.presentationModeTitle')">
      <p>{{ t('helpCenter.kbFileView.presentationModeText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.kbFileView.presentationModeKeys') }}</li>
        <li>{{ t('helpCenter.kbFileView.presentationModeClick') }}</li>
        <li>{{ t('helpCenter.kbFileView.presentationModeSwipe') }}</li>
        <li>{{ t('helpCenter.kbFileView.presentationModeEsc') }}</li>
      </BulletList>
      <p>{{ t('helpCenter.kbFileView.presentationModeAutoHide') }}</p>
    </HelpSection>

    <!-- Dummy: Conversion pending -->
    <NeutralContainer class="text-center py-4">
      <Spinner size="lg"/>
      <MutedText tag="p" class="mt-2">Die Datei wird in ein PDF umgewandelt...</MutedText>
    </NeutralContainer>
    <MutedText tag="p" size="sm" class="mt-1">{{ t('helpCenter.kbFileView.conversionHint') }}</MutedText>

    <HelpPermissionGuard :permissions="[StationPermission.KNOWLEDGE_EDIT]" :label="t('helpCenter.permissionLabel.knowledgeEdit')">
      <HelpSection :title="t('helpCenter.kbFileView.reuploadTitle')">
        <p>{{ t('helpCenter.kbFileView.reuploadText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.kbFileView.presentationTip') }}</HelpTip>

    <HelpTip>{{ t('helpCenter.kbFileView.tip') }}</HelpTip>
  </HelpArticle>
</template>
