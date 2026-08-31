/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import CreateInviteModal from './CreateInviteModal.vue'
import DeleteListModal from './DeleteListModal.vue'
import TransitionConfirmModal from './TransitionConfirmModal.vue'
import InviteEntryModal from './InviteEntryModal.vue'
import DeleteEntryModal from './DeleteEntryModal.vue'
import type { WaitingListEntryWithScore } from '@/api/waitingList'
import type { EventOccurrenceRef } from '@/api/events'

type TransitionKind = 'testing' | 'join' | 'approve' | 'reject' | 'withdraw' | 'backToWaiting'

interface PendingTransition {
  entry: WaitingListEntryWithScore
  kind: TransitionKind
}

const props = defineProps<{
  showInvite: boolean
  inviteMaxUses: number | undefined
  inviteExpiresAt: string
  creatingInvite: boolean
  showDelete: boolean
  listName: string | undefined
  deletingList: boolean
  pendingTransition: PendingTransition | null
  runningTransition: boolean
  inviteTarget: WaitingListEntryWithScore | null
  inviteOccurrence: EventOccurrenceRef | null
  inviteArrivalTime: string
  runningInvite: boolean
  showDeleteEntry: boolean
  deleteEntryTarget: WaitingListEntryWithScore | null
}>()

const emit = defineEmits<{
  (e: 'update:showInvite', value: boolean): void
  (e: 'update:inviteMaxUses', value: number | undefined): void
  (e: 'update:inviteExpiresAt', value: string): void
  (e: 'submit-invite'): void
  (e: 'update:showDelete', value: boolean): void
  (e: 'confirm-delete-list'): void
  (e: 'cancel-transition'): void
  (e: 'confirm-transition'): void
  (e: 'update:inviteOccurrence', value: EventOccurrenceRef | null): void
  (e: 'update:inviteArrivalTime', value: string): void
  (e: 'cancel-invite'): void
  (e: 'confirm-invite'): void
  (e: 'update:showDeleteEntry', value: boolean): void
  (e: 'confirm-delete-entry'): void
}>()

const showInviteModel = computed({
  get: () => props.showInvite,
  set: (v) => emit('update:showInvite', v),
})
const inviteMaxUsesModel = computed({
  get: () => props.inviteMaxUses,
  set: (v) => emit('update:inviteMaxUses', v),
})
const inviteExpiresAtModel = computed({
  get: () => props.inviteExpiresAt,
  set: (v) => emit('update:inviteExpiresAt', v),
})
const showDeleteModel = computed({
  get: () => props.showDelete,
  set: (v) => emit('update:showDelete', v),
})
const showDeleteEntryModel = computed({
  get: () => props.showDeleteEntry,
  set: (v) => emit('update:showDeleteEntry', v),
})
const inviteOccurrenceModel = computed({
  get: () => props.inviteOccurrence,
  set: (v) => emit('update:inviteOccurrence', v),
})
const inviteArrivalTimeModel = computed({
  get: () => props.inviteArrivalTime,
  set: (v) => emit('update:inviteArrivalTime', v),
})
</script>

<template>
  <CreateInviteModal
    v-model="showInviteModel"
    v-model:max-uses="inviteMaxUsesModel"
    v-model:expires-at="inviteExpiresAtModel"
    :creating="creatingInvite"
    @submit="emit('submit-invite')"
  />
  <DeleteListModal
    v-model="showDeleteModel"
    :list-name="listName"
    :deleting="deletingList"
    @confirm="emit('confirm-delete-list')"
  />
  <TransitionConfirmModal
    :pending="pendingTransition"
    :running="runningTransition"
    @cancel="emit('cancel-transition')"
    @confirm="emit('confirm-transition')"
  />
  <InviteEntryModal
    v-model:occurrence="inviteOccurrenceModel"
    v-model:arrival-time="inviteArrivalTimeModel"
    :target="inviteTarget"
    :running="runningInvite"
    @cancel="emit('cancel-invite')"
    @confirm="emit('confirm-invite')"
  />
  <DeleteEntryModal
    v-model="showDeleteEntryModel"
    :target="deleteEntryTarget"
    @confirm="emit('confirm-delete-entry')"
  />
</template>
