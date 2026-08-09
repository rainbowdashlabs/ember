/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DataTable from '@/components/table/DataTable.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import type {MemberSummary} from '@/api/attendance'

const {t} = useI18n()

defineProps<{
  title: string
  members: MemberSummary[]
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ title }}</SubHeader>
    <DataTable plain>
      <template #head>
        <th class="text-left py-2 px-3">{{ t('attendanceReport.name') }}</th>
        <Th align="center">{{ t('attendanceReport.sessions') }}</Th>
        <Th align="center">{{ t('attendanceReport.present') }}</Th>
        <th class="text-right py-2 px-3">{{ t('attendanceReport.hours') }}</th>
      </template>
      <TRow v-for="m in members" :key="m.memberId">
        <Td>{{ m.name }}</Td>
        <Td align="center">{{ m.sessionCount }}</Td>
        <Td align="center">{{ m.presentCount }}</Td>
        <Td align="right" class="font-mono">{{ m.totalHours.toFixed(1) }}</Td>
      </TRow>
    </DataTable>
  </NeutralContainer>
</template>
