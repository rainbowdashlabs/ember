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
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'

const {t} = useI18n()

const userTypeOptions = [
  { value: 'MEMBER', label: 'Mitglied' },
  { value: 'TEAM', label: 'Team' },
]
const groupOptions = [
  { value: '1', label: 'Anfänger' },
  { value: '2', label: 'Fortgeschrittene' },
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceReport.title')" :subtitle="t('helpCenter.attendanceReport.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceReport.whatIs')">
      <p>{{ t('helpCenter.attendanceReport.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceReport.filtersTitle')">
      <p>{{ t('helpCenter.attendanceReport.filtersText') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterPeriod') }}</p>
      <p>{{ t('helpCenter.attendanceReport.filterRounding') }}</p>
    </HelpSection>

    <!-- Dummy: Filter controls using real components -->
    <HelpSection :title="t('helpCenter.attendanceReport.filterExampleTitle')">
      <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('attendanceReport.filters') }}</SubHeader>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.userTypes') }}</FieldLabel>
            <MultiSelectDropdown
              :options="userTypeOptions"
              :model-value="['MEMBER']"
              :placeholder="t('attendanceReport.selectUserTypes')"
            />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.groups') }}</FieldLabel>
            <MultiSelectDropdown
              :options="groupOptions"
              :model-value="[]"
              :placeholder="t('attendanceReport.selectGroups')"
            />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.rounding') }}</FieldLabel>
            <SelectInput :model-value="'exact'" disabled>
              <option value="exact">Exakt (2 Dezimalstellen)</option>
              <option value="round">Gerundet (0.5h)</option>
              <option value="ceil">Aufgerundet (volle Stunde)</option>
            </SelectInput>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.period') }}</FieldLabel>
            <SelectInput :model-value="'month'" disabled>
              <option value="week">Woche</option>
              <option value="month">Monat</option>
              <option value="year">Jahr</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.year') }}</FieldLabel>
            <SelectInput :model-value="'2025'" disabled>
              <option>2025</option>
            </SelectInput>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('attendanceReport.month') }}</FieldLabel>
            <SelectInput :model-value="'5'" disabled>
              <option value="5">Juni</option>
            </SelectInput>
          </div>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <PrimaryButton :icon="['fas', 'eye']" disabled>
            {{ t('attendanceReport.preview') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'copy']" disabled>
            {{ t('attendanceReport.savePreset') }}
          </SecondaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Saved presets -->
    <HelpSection :title="t('helpCenter.attendanceReport.presetsTitle')">
      <p>{{ t('helpCenter.attendanceReport.presetsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceReport.previewTitle')">
      <p>{{ t('helpCenter.attendanceReport.previewText') }}</p>
      <p>{{ t('helpCenter.attendanceReport.previewSave') }}</p>
    </HelpSection>

    <!-- Dummy: Report preview table -->
    <HelpSection :title="t('helpCenter.attendanceReport.tableTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('attendanceReport.summary') }} – Mitglied</SubHeader>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
            <THead>
              <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
              <Th align="center">{{ t('attendanceReport.sessions') }}</Th>
              <Th align="center">{{ t('attendanceReport.present') }}</Th>
              <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</th>
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

      <div class="flex justify-end mt-3">
        <PrimaryButton :icon="['fas', 'download']" disabled>
          {{ t('attendanceReport.exportPdf') }}
        </PrimaryButton>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceReport.tip') }}</HelpTip>
  </HelpArticle>
</template>
