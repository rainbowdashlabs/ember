/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {ClusterItem} from '@/api/clusterInventory'
import type {OutItemTableApi} from './useOutItemTable'

/** The pieces one station holds, as its part of the table of everything that is out. */
defineProps<{
  station: string
  items: readonly ClusterItem[]
  table: OutItemTableApi
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="out-station-group">
    <div class="flex items-center justify-between gap-3">
      <SectionHeader>{{ station }}</SectionHeader>
      <SecondaryBadge>{{ t('clusterInventory.itemCount', {count: items.length}) }}</SecondaryBadge>
    </div>
    <RecordTable :subset="items" :table="table" plain row-test-id="out-item" test-id="out-item-table">
      <template #cell-size="{text}">
        <PrimaryBadge v-if="text">{{ text }}</PrimaryBadge>
      </template>
      <template #cell-custody="{text}">
        <SecondaryBadge>{{ text }}</SecondaryBadge>
      </template>
    </RecordTable>
  </NeutralContainer>
</template>
