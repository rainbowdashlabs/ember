/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import MemberCheckRow from './MemberCheckRow.vue'

const props = defineProps<{
  members: MemberCheckSummary[]
  currentMemberId: number | undefined
}>()

const emit = defineEmits<{
  (e: 'start-check', memberId: number): void
  (e: 'view-last-check', member: MemberCheckSummary): void
}>()

const {t} = useI18n()
</script>

<template>
  <DataTable class="hidden sm:block">
    <template #head>
      <Th>{{ t('inventory.check.member') }}</Th>
      <Th>{{ t('inventory.check.lastChecked') }}</Th>
      <Th>{{ t('inventory.check.checkedBy') }}</Th>
      <Th>{{ t('inventory.check.status') }}</Th>
      <th class="px-3 py-2"></th>
    </template>
    <MemberCheckRow
        v-for="member in members"
        :key="member.memberId"
        :member="member"
        :current-member-id="currentMemberId"
        @start-check="emit('start-check', $event)"
        @view-last-check="emit('view-last-check', $event)"
    />
  </DataTable>
</template>
