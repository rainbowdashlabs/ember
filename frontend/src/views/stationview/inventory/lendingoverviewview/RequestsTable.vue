/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {RouterLink} from 'vue-router'
import RecordTable from '@/components/table/RecordTable.vue'
import type {LendingRequestResponse} from '@/api/generated/schema'
import type {DataTableApi} from '@/composables/useDataTable'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import LendingStatusBadge from '../lendingrequestview/LendingStatusBadge.vue'

/** Every lending request in both directions; the station opens the request. */
defineProps<{
  table: DataTableApi<LendingRequestResponse>
}>()

const routes = useInventoryRoutes()

function link(row: LendingRequestResponse) {
  return routes.lendingRequest ? {name: routes.lendingRequest, params: {id: row.request.id}} : undefined
}
</script>

<template>
  <RecordTable :table="table" test-id="lending-requests-table" row-test-id="lending-request-row">
    <template #cell-station="{row, text}">
      <component :is="routes.lendingRequest ? RouterLink : 'span'" :to="link(row)" class="font-medium hover:underline">{{ text }}</component>
    </template>
    <template #cell-status="{row}">
      <LendingStatusBadge :status="row.request.status" :overdue="row.overdue"/>
    </template>
  </RecordTable>
</template>
