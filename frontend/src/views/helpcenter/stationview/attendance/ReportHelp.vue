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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceReport.title')" :subtitle="t('helpCenter.attendanceReport.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceReport.whatIs')">
      <p>{{ t('helpCenter.attendanceReport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceReport.filtersTitle')">
      <p>{{ t('helpCenter.attendanceReport.filterRoleGroup') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterPeriod') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterRounding') }}</p>
    </HelpSection>

    <!-- Dummy: Filter controls -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('attendanceReport.filters') }}</SubHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('attendanceReport.filterBy') }}</FieldLabel>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            {{ t('attendanceReport.byRole') }}
          </div>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('attendanceReport.role') }}</FieldLabel>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            MEMBER
          </div>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('attendanceReport.rounding') }}</FieldLabel>
          <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
            {{ t('attendanceReport.roundingExact') }}
          </div>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-wrap">
        <PrimaryButton :icon="['fas', 'eye']">
          {{ t('attendanceReport.preview') }}
        </PrimaryButton>
        <SecondaryButton :icon="['fas', 'copy']">
          {{ t('attendanceReport.savePreset') }}
        </SecondaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceReport.previewTitle')">
      <p>{{ t('helpCenter.attendanceReport.previewText') }}</p>
      <p>{{ t('helpCenter.attendanceReport.previewSave') }}</p>
    </HelpSection>

    <!-- Dummy: Report preview table -->
    <NeutralContainer class="space-y-3">
      <SubHeader>{{ t('attendanceReport.summary') }} – {{ t('membersList.tabMember') }}</SubHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
          <THead>
            <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
            <Th align="center">{{ t('attendanceReport.sessions') }}</th>
            <Th align="center">{{ t('attendanceReport.present') }}</th>
            <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</Th>
          </THead>
          </thead>
          <tbody>
          <TRow>
            <Td>Max Mustermann</Td>
            <Td align="center">8</Td>
            <Td align="center">7</Td>
            <Td align="right" class="font-mono">14.0</Td>
          </TRow>
          <TRow>
            <Td>Erika Muster</Td>
            <Td align="center">8</Td>
            <Td align="center">6</Td>
            <Td align="right" class="font-mono">12.5</Td>
          </TRow>
          </tbody>
        </table>
      </div>
    </NeutralContainer>

    <div class="flex justify-end">
      <PrimaryButton :icon="['fas', 'download']">
        {{ t('attendanceReport.exportPdf') }}
      </PrimaryButton>
    </div>

    <HelpTip>{{ t('helpCenter.attendanceReport.tip') }}</HelpTip>
  </HelpArticle>
</template>
