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
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.checklist.title')" :subtitle="t('helpCenter.checklist.subtitle')">
    <HelpSection :title="t('helpCenter.checklist.whatIs')">
      <p>{{ t('helpCenter.checklist.whatIsText') }}</p>
      <p>{{ t('helpCenter.checklist.whatIsText2') }}</p>
    </HelpSection>

    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('checklist.title') }}</SectionHeader>
      <HelpPermissionGuard :permissions="[StationPermission.CHECKLIST_MANAGE]" :label="t('helpCenter.permissionLabel.checklistManage')">
        <PrimaryButton disabled>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
          {{ t('checklist.create') }}
        </PrimaryButton>
      </HelpPermissionGuard>
    </div>

    <div class="space-y-2 mb-6">
      <SecondaryContainer class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex-1 min-w-0">
          <div class="font-semibold">Sicherheitsbriefing 2026</div>
          <div class="text-sm text-(--text-muted)">Pflichtschulung für alle Aktiven.</div>
          <div class="text-xs text-(--text-muted) mt-1">Zuletzt aufgefrischt: 14.06.2026 18:00</div>
        </div>
        <div class="flex flex-wrap gap-2 items-center">
          <SecondaryBadge>24 Mitglieder</SecondaryBadge>
          <InfoBadge>3 Spalten</InfoBadge>
        </div>
      </SecondaryContainer>
      <SecondaryContainer class="flex flex-wrap items-center justify-between gap-3">
        <div class="flex-1 min-w-0">
          <div class="font-semibold">Atemschutz-Übung Q3</div>
          <div class="text-xs text-(--text-muted) mt-1">Noch nicht aufgefrischt</div>
        </div>
        <div class="flex flex-wrap gap-2 items-center">
          <SecondaryBadge>11 Mitglieder</SecondaryBadge>
          <InfoBadge>5 Spalten</InfoBadge>
        </div>
      </SecondaryContainer>
    </div>

    <HelpSection :title="t('helpCenter.checklist.createTitle')">
      <p>{{ t('helpCenter.checklist.createText') }}</p>
      <ol class="list-decimal pl-6 space-y-1">
        <li>{{ t('helpCenter.checklist.step1') }}</li>
        <li>{{ t('helpCenter.checklist.step2') }}</li>
        <li>{{ t('helpCenter.checklist.step3') }}</li>
        <li>{{ t('helpCenter.checklist.step4') }}</li>
      </ol>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.followsTitle')">
      <p>{{ t('helpCenter.checklist.followsText') }}</p>
      <NeutralContainer>
        <ul class="list-disc pl-6 space-y-1 text-sm">
          <li>{{ t('helpCenter.checklist.followsOccurrence') }}</li>
          <li>{{ t('helpCenter.checklist.followsRefresh') }}</li>
          <li>{{ t('helpCenter.checklist.followsCancel') }}</li>
          <li>{{ t('helpCenter.checklist.followsGuests') }}</li>
          <li>{{ t('helpCenter.checklist.followsDeleted') }}</li>
        </ul>
      </NeutralContainer>
      <HelpTip>{{ t('helpCenter.checklist.followsSwitch') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.refreshTitle')">
      <p>{{ t('helpCenter.checklist.refreshText') }}</p>
      <NeutralContainer>
        <ul class="list-disc pl-6 space-y-1 text-sm">
          <li>{{ t('helpCenter.checklist.refreshAdditive') }}</li>
          <li>{{ t('helpCenter.checklist.refreshNoResurrect') }}</li>
          <li>{{ t('helpCenter.checklist.refreshNoRemove') }}</li>
        </ul>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.manualTitle')">
      <p>{{ t('helpCenter.checklist.manualText') }}</p>
      <HelpTip>{{ t('helpCenter.checklist.manualTip') }}</HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.checklist.exportTitle')">
      <p>{{ t('helpCenter.checklist.exportText') }}</p>
      <ul class="list-disc pl-6 space-y-1">
        <li>{{ t('helpCenter.checklist.exportCsvText') }}</li>
        <li>{{ t('helpCenter.checklist.exportPdfText') }}</li>
      </ul>
    </HelpSection>
  </HelpArticle>
</template>
