/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import type {OutItemTableApi} from './useOutItemTable'

/** Narrows what is out to one station, and chooses the columns every station's block shows. */
defineProps<{
  stations: string[]
  table: OutItemTableApi
}>()

const station = defineModel<string>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-end justify-between gap-2">
    <div class="space-y-1 w-full max-w-xs">
      <FieldLabel>{{ t('clusterInventory.stationFilter') }}</FieldLabel>
      <SelectInput v-model="station" data-testid="out-station-filter">
        <option value="">{{ t('clusterInventory.stationFilterAll') }}</option>
        <option v-for="name in stations" :key="name" :value="name">{{ name }}</option>
      </SelectInput>
    </div>
    <TableColumnPicker :table="table"/>
  </div>
</template>
