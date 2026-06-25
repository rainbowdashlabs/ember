/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script setup lang="ts">
import { computed } from 'vue'
import CreateInviteModal from './CreateInviteModal.vue'
import DeleteListModal from './DeleteListModal.vue'
import TransitionConfirmModal from './TransitionConfirmModal.vue'
import DeleteEntryModal from './DeleteEntryModal.vue'
import type { WaitingListEntryWithScore } from '@/api/types'

type TransitionKind = 'invite' | 'testing' | 'join' | 'approve' | 'reject' | 'withdraw'

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
  <DeleteEntryModal
    v-model="showDeleteEntryModel"
    :target="deleteEntryTarget"
    @confirm="emit('confirm-delete-entry')"
  />
</template>
