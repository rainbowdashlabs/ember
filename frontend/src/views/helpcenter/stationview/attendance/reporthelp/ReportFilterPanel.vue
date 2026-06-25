/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

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
</template>
