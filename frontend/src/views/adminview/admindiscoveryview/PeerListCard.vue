/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PeerRow from './PeerRow.vue'
import type {DiscoveryPeerSource} from '@/api/discovery'
import type {PeerResponse} from '@/api/generated/schema'

defineProps<{
  peers: PeerResponse[]
  sourceLabel: Record<DiscoveryPeerSource, string>
  inFlightKey: string | null
}>()

const emit = defineEmits<{
  upvote: [peer: PeerResponse]
  downvote: [peer: PeerResponse]
  block: [peer: PeerResponse]
  unblock: [peer: PeerResponse]
  ping: [peer: PeerResponse]
  remove: [peer: PeerResponse]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminDiscovery.peers') }}</SubHeader>
    <EmptyState v-if="peers.length === 0">{{ t('adminDiscovery.peersEmpty') }}</EmptyState>
    <div v-else class="space-y-2">
      <PeerRow
          v-for="p in peers"
          :key="p.publicKey"
          :peer="p"
          :source-label="sourceLabel[p.source]"
          :in-flight="inFlightKey === p.publicKey"
          @upvote="emit('upvote', p)"
          @downvote="emit('downvote', p)"
          @block="emit('block', p)"
          @unblock="emit('unblock', p)"
          @ping="emit('ping', p)"
          @remove="emit('remove', p)"
      />
    </div>
  </NeutralContainer>
</template>
