/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import { ColumnTypes, type TableColumn } from '@/components/table/tableColumn'
import type { StationMember } from '@/api/types'
import { emptyTableState, useDataTable } from '@/composables/useDataTable'

/**
 * The people who have left the station, sortable and filterable by name, address and leaving day.
 * The ones who left most recently come first.
 */
const props = defineProps<{
  members: StationMember[]
  memberDisplayName: (m: StationMember) => string
}>()

defineEmits<{
  (e: 'reactivate', member: StationMember): void
}>()

const { t } = useI18n()

const columns = computed<TableColumn<StationMember>[]>(() => [
  { key: 'name', label: t('membersList.colName'), type: ColumnTypes.TEXT, value: props.memberDisplayName },
  { key: 'email', label: t('membersList.colEmail'), type: ColumnTypes.TEXT, value: member => member.email },
  { key: 'formerAt', label: t('formerMembers.colFormerAt'), type: ColumnTypes.DATE, value: member => member.formerAt },
])

const table = useDataTable<StationMember>({
  id: 'former-members',
  rows: () => props.members,
  columns,
  rowKey: member => member.id,
  state: ref(emptyTableState('formerAt', 'desc')),
})
</script>

<template>
  <RecordTable :table="table" test-id="former-members-table">
    <template #cell-name="{text}">
      <span class="font-medium text-(--text-muted)">{{ text }}</span>
    </template>
    <template #actions="{row}">
      <PrimaryButton :icon="['fas', 'user-check']" @click="$emit('reactivate', row)">
        {{ t('formerMembers.reactivate') }}
      </PrimaryButton>
    </template>
  </RecordTable>
</template>
