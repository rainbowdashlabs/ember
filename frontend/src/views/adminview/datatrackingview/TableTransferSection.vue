/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {TrackingStatusName, TransferContext} from '@/api/dataTracking'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{
  transfer: TransferContext
  statuses: TrackingStatusName[]
  columnOptions: { value: string; label: string; group?: string }[]
}>()

const {t} = useI18n()

const ignoredColumns = computed({
  get: () => props.transfer.ignoredColumns ?? [],
  set: (v: string[]) => { props.transfer.ignoredColumns = [...v] },
})
</script>

<template>
  <section>
    <div class="flex items-center justify-between">
      <SectionHeader class="!text-base">{{ t('adminDataTracking.stationTransfer') }}</SectionHeader>
      <StatusBadge :status="transfer.status"/>
    </div>
    <div class="space-y-2 mt-2">
      <SelectInput v-model="transfer.status">
        <option v-for="s in statuses" :key="s" :value="s">{{ s }}</option>
      </SelectInput>
      <TextInput
          v-if="transfer.status === 'IGNORED'"
          v-model="transfer.reason"
          :placeholder="t('adminDataTracking.detail.reason')"
      />
      <TextAreaInput
          v-if="transfer.status === 'TRACKED'"
          v-model="transfer.rationale"
          :placeholder="t('adminDataTracking.detail.rationale')"
          :rows="2"
      />
      <TextAreaInput
          v-if="transfer.status === 'UNVERIFIED'"
          v-model="transfer.rationale"
          :placeholder="t('adminDataTracking.detail.reviewNote')"
          :rows="2"
      />
      <div>
        <label class="block text-xs text-(--text-muted) mb-1">
          {{ t('adminDataTracking.detail.ignoredColumns') }}
        </label>
        <MultiSelectDropdown
            v-model="ignoredColumns"
            :options="columnOptions"
            :placeholder="t('adminDataTracking.detail.ignoredColumnsPlaceholder')"
            searchable
        />
      </div>
    </div>
  </section>
</template>
