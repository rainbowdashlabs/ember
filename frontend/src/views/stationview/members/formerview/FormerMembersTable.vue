/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import type { StationMember } from '@/api/types'

defineProps<{
  members: StationMember[]
  memberDisplayName: (m: StationMember) => string
  formatDate: (d?: string | null) => string
}>()

defineEmits<{
  (e: 'reactivate', member: StationMember): void
}>()

const { t } = useI18n()
</script>

<template>
  <DataTable>
    <template #head>
      <Th>{{ t('membersList.colName') }}</Th>
      <Th>{{ t('membersList.colEmail') }}</Th>
      <Th>{{ t('formerMembers.colFormerAt') }}</Th>
      <th class="px-3 py-2"></th>
    </template>
    <TRow v-for="member in members" :key="member.id">
      <Td class="font-medium text-(--text-muted)">{{ memberDisplayName(member) }}</Td>
      <Td muted>{{ member.email ?? '' }}</Td>
      <Td muted>{{ formatDate(member.formerAt) }}</Td>
      <Td align="right">
        <PrimaryButton :icon="['fas', 'user-check']" @click="$emit('reactivate', member)">
          {{ t('formerMembers.reactivate') }}
        </PrimaryButton>
      </Td>
    </TRow>
  </DataTable>
</template>
