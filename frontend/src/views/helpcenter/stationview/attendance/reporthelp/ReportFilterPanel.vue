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
import ButtonRow from '@/components/button/ButtonRow.vue'
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
          <option value="exact">{{ t('helpCenter.sample.attendance.roundExact') }}</option>
          <option value="round">{{ t('helpCenter.sample.attendance.roundHalf') }}</option>
          <option value="ceil">{{ t('helpCenter.sample.attendance.roundUp') }}</option>
        </SelectInput>
      </div>
    </div>

    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceReport.period') }}</FieldLabel>
        <SelectInput :model-value="'month'" disabled>
          <option value="week">{{ t('helpCenter.sample.attendance.week') }}</option>
          <option value="month">{{ t('helpCenter.sample.attendance.month') }}</option>
          <option value="year">{{ t('helpCenter.sample.attendance.year') }}</option>
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
          <option value="5">{{ t('helpCenter.sample.attendance.june') }}</option>
        </SelectInput>
      </div>
    </div>
    <ButtonRow>
      <PrimaryButton :icon="['fas', 'eye']" disabled>
        {{ t('attendanceReport.preview') }}
      </PrimaryButton>
      <SecondaryButton :icon="['fas', 'copy']" disabled>
        {{ t('attendanceReport.savePreset') }}
      </SecondaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
