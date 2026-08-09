/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ExchangeCreateModal from './ExchangeCreateModal.vue'
import ExchangeLogModal from './ExchangeLogModal.vue'
import type { ExchangeRequestEntry } from '@/api/exchanges'
import type { StationMember } from '@/api/types'
import type { ManagedMember } from '@/api/managedMembers'

const showCreate = defineModel<boolean>('showCreate', { required: true })
const showLog = defineModel<boolean>('showLog', { required: true })

defineProps<{
  requests: ExchangeRequestEntry[]
  membersWithItems: Set<number>
  membersWithItemsList: StationMember[]
  managedWithItemsList: ManagedMember[]
  managed: ManagedMember[]
  logExchangeId: number | null
}>()

const emit = defineEmits<{
  (e: 'created'): void
  (e: 'error', msg: string): void
}>()
</script>

<template>
  <ExchangeCreateModal
    v-model="showCreate"
    :requests="requests"
    :members-with-items="membersWithItems"
    :members-with-items-list="membersWithItemsList"
    :managed-with-items-list="managedWithItemsList"
    :managed="managed"
    @created="emit('created')"
    @error="(msg) => emit('error', msg)"
  />
  <ExchangeLogModal
    v-model="showLog"
    :exchange-id="logExchangeId"
    @error="(msg) => emit('error', msg)"
  />
</template>
