/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import ApplicationStatusBadge from '@/views/adminview/adminapplicationsview/ApplicationStatusBadge.vue'
import type {StationApplication} from '@/api/stationApplications'

defineProps<{
  applications: StationApplication[]
  processing: boolean
  formatDate: (dateStr?: string | null) => string
}>()

defineEmits<{
  accept: [app: StationApplication]
  deny: [app: StationApplication]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <THead>
        <Th>{{ t('adminApplications.name') }}</Th>
        <Th>{{ t('adminApplications.email') }}</Th>
        <Th>{{ t('adminApplications.station') }}</Th>
        <Th>{{ t('adminApplications.date') }}</Th>
        <Th>{{ t('adminApplications.status') }}</Th>
        <th class="px-3 py-2"></th>
      </THead>
      </thead>
      <tbody>
      <TRow v-for="app in applications" :key="app.id">
        <Td>
          <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
          <div v-if="app.introduction" :title="app.introduction"
               class="text-xs text-(--text-muted) mt-0.5 max-w-xs truncate">{{ app.introduction }}
          </div>
        </Td>
        <Td muted>{{ app.email }}</Td>
        <Td>{{ app.stationName }}</Td>
        <Td muted>{{ formatDate(app.createdAt) }}</Td>
        <Td>
          <ApplicationStatusBadge :status="app.status"/>
        </Td>
        <Td align="right">
          <div v-if="app.status === 'pending'" class="flex items-center justify-end gap-1">
            <PrimaryButton :disabled="processing" @click="$emit('accept', app)">
              {{ t('adminApplications.accept') }}
            </PrimaryButton>
            <ErrorButton :disabled="processing" @click="$emit('deny', app)">
              {{ t('adminApplications.deny') }}
            </ErrorButton>
          </div>
          <div v-else-if="app.status === 'denied' && app.denyReason" class="text-xs text-(--text-muted)">
            {{ app.denyReason }}
          </div>
        </Td>
      </TRow>
      </tbody>
    </table>
  </NeutralContainer>
</template>
