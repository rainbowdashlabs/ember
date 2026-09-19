/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useDataTable} from '@/composables/useDataTable'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import type {Movement} from '@/api/movements'
import MovementRecordTable from '../movementqueueview/MovementRecordTable.vue'
import {MovementColumn, type MovementColumnKey, useMovementColumns} from '../movementqueueview/movementColumns'

/**
 * What is on the move, as the inventory overview shows it: the piece, whose it is, and the step it
 * stands on. No status, because a movement has none. A press on a row leads to the queue, where it
 * is worked.
 */
const props = defineProps<{
  movements: Movement[]
}>()

const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()

const SHOWN: MovementColumnKey[] = [
  MovementColumn.SUBJECT,
  MovementColumn.PURPOSE,
  MovementColumn.MEMBER,
  MovementColumn.STANDING,
  MovementColumn.CREATED,
]

const allColumns = useMovementColumns()
const columns = computed(() => allColumns.value.filter(column => SHOWN.includes(column.key as MovementColumnKey)))

const table = useDataTable<Movement>({
  id: 'inventory-overview-movements',
  rows: () => props.movements,
  columns,
  rowKey: movement => movement.id,
})

function toQueue() {
  if (routes.movements) void router.push({name: routes.movements})
}
</script>

<template>
  <div class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2"/>
      {{ t('inventory.overview.exchanges') }} ({{ props.movements.length }})
    </SubHeader>
    <MovementRecordTable :table="table" clickable test-id="overview-movements" @row-click="toQueue"/>
  </div>
</template>
