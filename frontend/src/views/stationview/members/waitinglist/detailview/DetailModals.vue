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
import type { useListInvites } from './useListInvites'
import type { useEntryTransitions } from './useEntryTransitions'
import type { useEntryInvitation } from './useEntryInvitation'
import type { WaitingListEntryWithScore } from '@/api/waitingList'

/**
 * Every window the list detail page can open.
 *
 * It is handed the three composables that own the work rather than a prop for each of their
 * fields: what a modal shows and what it does when confirmed belong together, and splitting them
 * into two dozen props and emits only moves the wiring somewhere it reads worse.
 */
const props = defineProps<{
  invite: ReturnType<typeof useListInvites>
  transitions: ReturnType<typeof useEntryTransitions>
  invitation: ReturnType<typeof useEntryInvitation>
  showDelete: boolean
  listName: string | undefined
  deletingList: boolean
  showDeleteEntry: boolean
  deleteEntryTarget: WaitingListEntryWithScore | null
}>()

const emit = defineEmits<{
  (e: 'update:showDelete', value: boolean): void
  (e: 'confirm-delete-list'): void
  (e: 'update:showDeleteEntry', value: boolean): void
  (e: 'confirm-delete-entry'): void
}>()

const showDeleteModel = computed({
  get: () => props.showDelete,
  set: (v) => emit('update:showDelete', v),
})
const showDeleteEntryModel = computed({
  get: () => props.showDeleteEntry,
  set: (v) => emit('update:showDeleteEntry', v),
})
</script>

<template>
  <CreateInviteModal
    v-model="invite.showModal.value"
    v-model:max-uses="invite.maxUses.value"
    v-model:expires-at="invite.expiresAt.value"
    :creating="invite.creating.value"
    @submit="invite.create"
  />
  <DeleteListModal
    v-model="showDeleteModel"
    :list-name="listName"
    :deleting="deletingList"
    @confirm="emit('confirm-delete-list')"
  />
  <TransitionConfirmModal
    :pending="transitions.pending.value"
    :running="transitions.running.value"
    @cancel="transitions.pending.value = null"
    @confirm="transitions.confirm"
  />
  <InviteEntryModal
    v-model:occurrence="invitation.occurrence.value"
    v-model:arrival-time="invitation.arrivalTime.value"
    :target="invitation.target.value"
    :running="invitation.running.value"
    @cancel="invitation.cancel"
    @confirm="invitation.confirm"
  />
  <DeleteEntryModal
    v-model="showDeleteEntryModel"
    :target="deleteEntryTarget"
    @confirm="emit('confirm-delete-entry')"
  />
</template>
