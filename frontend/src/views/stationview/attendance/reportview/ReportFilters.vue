/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ReportFilterAudience from './ReportFilterAudience.vue'
import ReportFilterPeriod from './ReportFilterPeriod.vue'
import ReportFilterActions from './ReportFilterActions.vue'

const {t} = useI18n()

interface Option {
  value: string
  label: string
}

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<string[]>('selectedGroupIds', {required: true})
const selectedRounding = defineModel<string>('selectedRounding', {required: true})
const selectedPeriod = defineModel<string>('selectedPeriod', {required: true})
const selectedYear = defineModel<number>('selectedYear', {required: true})
const selectedMonth = defineModel<number>('selectedMonth', {required: true})
const selectedWeek = defineModel<number>('selectedWeek', {required: true})
const showSavePreset = defineModel<boolean>('showSavePreset', {required: true})
const presetName = defineModel<string>('presetName', {required: true})

defineProps<{
  userTypeOptions: Option[]
  groupOptions: Option[]
  roundingOptions: Option[]
  periodOptions: Option[]
  yearOptions: number[]
  monthOptions: { value: number; label: string }[]
  weekOptions: number[]
  canPreview: boolean
  previewing: boolean
  savePreset: () => Promise<void>
}>()

const emit = defineEmits<{
  preview: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('attendanceReport.filters') }}</SubHeader>
    <ReportFilterAudience
        v-model:selected-user-types="selectedUserTypes"
        v-model:selected-group-ids="selectedGroupIds"
        v-model:selected-rounding="selectedRounding"
        :user-type-options="userTypeOptions"
        :group-options="groupOptions"
        :rounding-options="roundingOptions"
    />
    <ReportFilterPeriod
        :period-options="periodOptions"
        :year-options="yearOptions"
        :month-options="monthOptions"
        :week-options="weekOptions"
        :selected-period="selectedPeriod"
        :selected-year="selectedYear"
        :selected-month="selectedMonth"
        :selected-week="selectedWeek"
        @update:selected-period="selectedPeriod = $event"
        @update:selected-year="selectedYear = $event"
        @update:selected-month="selectedMonth = $event"
        @update:selected-week="selectedWeek = $event"
    />
    <ReportFilterActions
        v-model:show-save-preset="showSavePreset"
        v-model:preset-name="presetName"
        :can-preview="canPreview"
        :previewing="previewing"
        :save-preset="savePreset"
        @preview="emit('preview')"
    />
  </NeutralContainer>
</template>
