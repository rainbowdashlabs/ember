/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import { PairRequestStatus, type OutgoingPairRequestResponse } from '@/api/generated/schema'
import { formatDate } from '@/util/format'

defineProps<{
  requests: OutgoingPairRequestResponse[]
}>()

const { t } = useI18n()
</script>

<template>
  <div v-if="requests.length > 0" class="mb-6">
    <SubHeader class="mb-2">{{ t('federation.outgoingRequests') }}</SubHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="request in requests" :key="request.id" class="flex items-center gap-2">
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ request.stationName }}</div>
          <div class="text-xs text-[var(--text-muted)]">
            {{ t('federation.onInstance', { host: request.instanceHost }) }} · {{ formatDate(request.createdAt) }}
          </div>
        </div>
        <ErrorBadge v-if="request.status === PairRequestStatus.DECLINED">{{ t('federation.declined') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('federation.waitingForAnswer') }}</SecondaryBadge>
      </NeutralContainer>
    </div>
  </div>
</template>
