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
import ApplicationStatusBadge from '@/views/adminview/adminapplicationsview/ApplicationStatusBadge.vue'
import type {StationApplication} from '@/api/stationApplications'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()

defineProps<{
  applications: StationApplication[]
  processing: boolean
}>()

defineEmits<{
  accept: [app: StationApplication]
  deny: [app: StationApplication]
}>()
</script>

<template>
  <div class="space-y-3">
    <NeutralContainer v-for="app in applications" :key="app.id" class="space-y-2">
      <div class="flex items-center justify-between">
        <div class="font-medium">{{ app.firstName }} {{ app.lastName }}</div>
        <ApplicationStatusBadge :status="app.status"/>
      </div>
      <div class="grid grid-cols-1 gap-1 text-xs">
        <div class="text-(--text-muted)">{{ app.email }}</div>
        <div><span class="text-(--text-muted)">{{ t('adminApplications.station') }}:</span> {{ app.stationName }}</div>
        <div class="text-(--text-muted)">{{ formatDateTime(app.createdAt) || '-' }}</div>
        <div v-if="app.introduction" class="text-(--text-muted) truncate">{{ app.introduction }}</div>
      </div>
      <div v-if="app.status === 'pending'"
           class="flex gap-1 pt-1 border-t border-bg-light-accent/50 dark:border-bg-dark-accent/50">
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
    </NeutralContainer>
  </div>
</template>
