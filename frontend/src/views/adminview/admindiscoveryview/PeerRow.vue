/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import type {DiscoveryPeer} from '@/api/discovery'
import {formatDateTime} from '@/util/format'

defineProps<{
  peer: DiscoveryPeer
  sourceLabel: string
  inFlight: boolean
}>()

const emit = defineEmits<{
  upvote: []
  downvote: []
  block: []
  unblock: []
  ping: []
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="rounded-(--radius-theme) border border-bg-light-accent dark:border-bg-dark-accent p-3 space-y-2">
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="space-y-1 min-w-0">
        <p class="font-medium break-all">{{ peer.baseUrl }}</p>
        <p class="text-xs text-(--text-muted)">
          <code>{{ peer.instanceId }}</code>
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <SecondaryBadge>{{ sourceLabel }}</SecondaryBadge>
        <PrimaryBadge v-if="peer.reachable">{{ t('adminDiscovery.peerTable.reachable') }}</PrimaryBadge>
        <ErrorBadge v-else>{{ t('adminDiscovery.no') }}</ErrorBadge>
        <ErrorBadge v-if="peer.blocked">{{ t('adminDiscovery.peerTable.blocked') }}</ErrorBadge>
        <SuccessBadge v-if="peer.reputation >= 0">{{ peer.reputation }}</SuccessBadge>
        <ErrorBadge v-else>{{ peer.reputation }}</ErrorBadge>
      </div>
    </div>
    <p class="text-xs text-(--text-muted)">
      {{ t('adminDiscovery.peerTable.lastSeen') }}: {{ formatDateTime(peer.lastSeenAt) || '-' }}
    </p>
    <div class="flex flex-wrap gap-1">
      <IconButton :icon="['fas', 'arrow-up']" :label="t('adminDiscovery.upvote')" :disabled="inFlight" @click="emit('upvote')"/>
      <IconButton :icon="['fas', 'arrow-down']" :label="t('adminDiscovery.downvote')" :disabled="inFlight" @click="emit('downvote')"/>
      <IconButton v-if="!peer.blocked" :icon="['fas', 'ban']" :label="t('adminDiscovery.block')" :disabled="inFlight" @click="emit('block')"/>
      <IconButton v-else :icon="['fas', 'check']" :label="t('adminDiscovery.unblock')" :disabled="inFlight" @click="emit('unblock')"/>
      <IconButton :icon="['fas', 'satellite-dish']" :label="t('adminDiscovery.pingNow')" :disabled="inFlight" @click="emit('ping')"/>
      <DeleteButton :disabled="inFlight" @click="emit('remove')"/>
    </div>
  </div>
</template>
