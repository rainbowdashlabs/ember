/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { PairRequestResponse, RemotePairRequestResponse } from '@/api/generated/schema'
import { formatDate } from '@/util/format'

/**
 * One request waiting for this station's answer, from a station of this instance or of another one.
 * A request of another instance names the instance it comes from, since its station is otherwise a
 * stranger to everybody here.
 */
export interface IncomingPairRequest {
  id: number
  stationName: string
  instanceHost: string | null
  createdAt: string
}

const props = defineProps<{
  local: PairRequestResponse[]
  remote: RemotePairRequestResponse[]
}>()

const emit = defineEmits<{
  accept: [request: IncomingPairRequest]
  decline: [request: IncomingPairRequest]
}>()

const { t } = useI18n()

const requests = computed<IncomingPairRequest[]>(() => [
  ...props.local.map(request => ({ ...request, instanceHost: null })),
  ...props.remote,
])
</script>

<template>
  <div v-if="requests.length > 0" class="mb-6">
    <SubHeader class="mb-2">{{ t('federation.pairRequests') }}</SubHeader>
    <div class="space-y-2">
      <NeutralContainer
          v-for="request in requests"
          :key="`${request.instanceHost ?? ''}/${request.id}`"
          class="flex items-center gap-2"
      >
        <div class="flex-1 min-w-0">
          <div class="font-medium">{{ request.stationName }}</div>
          <div class="text-xs text-[var(--text-muted)]">
            <span v-if="request.instanceHost">{{ t('federation.fromInstance', { host: request.instanceHost }) }} · </span>
            {{ formatDate(request.createdAt) }}
          </div>
        </div>
        <SecondaryButton compact @click="emit('accept', request)">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/> {{ t('federation.acceptRequest') }}
        </SecondaryButton>
        <DeleteButton @click="emit('decline', request)"/>
      </NeutralContainer>
    </div>
  </div>
</template>
