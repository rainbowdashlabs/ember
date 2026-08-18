/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DataTable from '@/components/table/DataTable.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import TRow from '@/components/table/TRow.vue'
import ApplicationStatusBadge from '@/views/adminview/adminapplicationsview/ApplicationStatusBadge.vue'
import {ApplicationStatus, type StationApplication} from '@/api/stationApplications'
import {formatDateTime} from '@/util/format'

defineProps<{
  applications: StationApplication[]
  processing: boolean
}>()

defineEmits<{
  accept: [app: StationApplication]
  deny: [app: StationApplication]
}>()

const {t} = useI18n()
</script>

<template>
  <DataTable>
    <template #head>
      <Th>{{ t('adminApplications.name') }}</Th>
      <Th>{{ t('adminApplications.email') }}</Th>
      <Th>{{ t('adminApplications.station') }}</Th>
      <Th>{{ t('adminApplications.date') }}</Th>
      <Th>{{ t('adminApplications.status') }}</Th>
      <th class="px-3 py-2"></th>
    </template>
    <TRow v-for="app in applications" :key="app.id" data-testid="application-entry">
      <Td>
        <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
        <MutedText v-if="app.introduction" :title="app.introduction"
                   tag="div" class="mt-0.5 max-w-xs truncate">{{ app.introduction }}
        </MutedText>
      </Td>
      <Td muted>{{ app.email }}</Td>
      <Td>{{ app.stationName }}</Td>
      <Td muted>{{ formatDateTime(app.createdAt) || '-' }}</Td>
      <Td>
        <ApplicationStatusBadge :status="app.status"/>
      </Td>
      <Td align="right">
        <div v-if="app.status === ApplicationStatus.PENDING" class="flex items-center justify-end gap-1">
          <PrimaryButton :disabled="processing" @click="$emit('accept', app)">
            {{ t('adminApplications.accept') }}
          </PrimaryButton>
          <ErrorButton :disabled="processing" @click="$emit('deny', app)">
            {{ t('adminApplications.deny') }}
          </ErrorButton>
        </div>
        <MutedText v-else-if="app.status === ApplicationStatus.DENIED && app.denyReason" tag="div">
          {{ app.denyReason }}
        </MutedText>
      </Td>
    </TRow>
  </DataTable>
</template>
