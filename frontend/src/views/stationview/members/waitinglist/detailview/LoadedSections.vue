/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import OverviewSection from './OverviewSection.vue'
import PendingSection from './PendingSection.vue'
import WaitingSection from './WaitingSection.vue'
import TestingSection from './TestingSection.vue'
import FinishedSection from './FinishedSection.vue'
import InvitesSection from './InvitesSection.vue'
import type {
  WaitingList,
  WaitingListEntryWithScore,
  WaitingListField,
  WaitingListInvite,
} from '@/api/waitingList'
import type {MemberGroup} from '@/api/types'
import type {Failure} from '@/util/failure'

export interface EntryGroups {
  pending: WaitingListEntryWithScore[]
  waiting: WaitingListEntryWithScore[]
  testing: WaitingListEntryWithScore[]
  finished: WaitingListEntryWithScore[]
}

export interface DetailPermissions {
  canManage: boolean
  canEdit: boolean
  canAdd: boolean
  canReadMembers: boolean
}

export interface DetailActions {
  onListUpdated: (list: WaitingList) => void
  onError: (failure: Failure) => void
  onSuccess: (message: string) => void
  onApprove: (id: number) => void
  onReject: (id: number) => void
  onInvite: (id: number) => void
  onBackToWaiting: (id: number) => void
  onMoveToTesting: (id: number) => void
  onMoveToJoined: (id: number) => void
  onWithdraw: (id: number) => void
  onNavigateToEntry: (id: number) => void
  onDeleteEntry: (entry: WaitingListEntryWithScore) => void
  onSetFields: (ids: number[], visible: boolean) => void
  onAddEntry: () => void
  onCreateInvite: () => void
  onDeleteInvite: (id: number) => void
  onCopyLink: (code: string) => void
}

defineProps<{
  list: WaitingList
  listId: number
  fields: WaitingListField[]
  groups: MemberGroup[]
  invites: WaitingListInvite[]
  entryGroups: EntryGroups
  visibleFieldIds: Set<number>
  permissions: DetailPermissions
  actions: DetailActions
}>()
</script>

<template>
  <OverviewSection
    :list="list"
    :list-id="listId"
    :fields="fields"
    :groups="groups"
    :readonly="!permissions.canManage"
    @updated="actions.onListUpdated"
    @error="actions.onError"
    @success="actions.onSuccess"
  />

  <PendingSection
    :entries="entryGroups.pending"
    :fields="fields"
    :readonly="!permissions.canEdit"
    @approve="actions.onApprove"
    @reject="actions.onReject"
  />

  <WaitingSection
    :entries="entryGroups.waiting"
    :fields="fields"
    :list-id="listId"
    :visible-field-ids="visibleFieldIds"
    :readonly="!permissions.canEdit"
    :can-add="permissions.canAdd"
    @invite="actions.onInvite"
    @back-to-waiting="actions.onBackToWaiting"
    @move-to-testing="actions.onMoveToTesting"
    @navigate-to-entry="actions.onNavigateToEntry"
    @delete-entry="actions.onDeleteEntry"
    @set-fields="actions.onSetFields"
    @add-entry="actions.onAddEntry"
  />

  <TestingSection
    :entries="entryGroups.testing"
    :attendance-threshold="list.attendanceThreshold ?? 5"
    :list-id="listId"
    :readonly="!permissions.canEdit"
    @move-to-joined="actions.onMoveToJoined"
    @withdraw="actions.onWithdraw"
  />

  <FinishedSection
    :entries="entryGroups.finished"
    :can-link-member="permissions.canReadMembers"
  />

  <InvitesSection
    v-if="permissions.canManage"
    :invites="invites"
    @create-invite="actions.onCreateInvite"
    @delete-invite="actions.onDeleteInvite"
    @copy-link="actions.onCopyLink"
  />
</template>
