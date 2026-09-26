/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DetailHeader from './detailview/DetailHeader.vue'
import LoadedSections from './detailview/LoadedSections.vue'
import DetailModals from './detailview/DetailModals.vue'
import type {
  WaitingList,
  WaitingListEntryWithScore,
  WaitingListField,
  WaitingListInvite,
} from '@/api/waitingList'
import {StationPermission, type MemberGroup} from '@/api/types'
import { waitingList, memberGroups } from '@/api'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { describeFailure, type Failure } from '@/util/failure'
import { useListInvites } from './detailview/useListInvites'
import { useEntryTransitions } from './detailview/useEntryTransitions'
import { useEntryInvitation } from './detailview/useEntryInvitation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { refresh: refreshSidebarCounts } = useSidebarCounts()
const { hasPermission } = useSession()

const canManage = computed(() => hasPermission(StationPermission.WAITLIST_MANAGER))
const canEdit = computed(() => hasPermission(StationPermission.WAITLIST_EDIT))
const canAdd = computed(() => hasPermission(StationPermission.WAITLIST_ADD))
const canReadMembers = computed(() => hasPermission(StationPermission.MEMBER_READ))

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const entries = ref<WaitingListEntryWithScore[]>([])
const invites = ref<WaitingListInvite[]>([])
const fields = ref<WaitingListField[]>([])
const groups = ref<MemberGroup[]>([])
const { message: success, flash } = useFlashMessage(3000)

const showDeleteModal = ref(false)

const sortedEntries = computed(() =>
  [...entries.value].sort((a, b) => b.score - a.score),
)
const pendingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'PENDING'),
)
const waitingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'WAITING' || e.entry.status === 'INVITED'),
)
const testingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'TESTING'),
)
const finishedEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'JOINED' || e.entry.status === 'WITHDRAWN'),
)

const visibleFieldIds = computed(() => new Set(list.value?.visibleFields ?? []))

/**
 * The list's own name at the head of the page, because a station keeps several of them and
 * "Warteliste" is the same word above each. The word stands there until the list has arrived, and
 * where it could not be fetched at all.
 */
const pageTitle = computed(() => list.value?.name || t('pages.waiting-list-detail.title'))

const entryGroups = computed(() => ({
  pending: pendingEntries.value,
  waiting: waitingEntries.value,
  testing: testingEntries.value,
  finished: finishedEntries.value,
}))

const permissions = computed(() => ({
  canManage: canManage.value,
  canEdit: canEdit.value,
  canAdd: canAdd.value,
  canReadMembers: canReadMembers.value,
}))

const sectionActions = computed(() => ({
  onListUpdated: handleListUpdated,
  onError: showFailure,
  onSuccess: showSuccessMessage,
  onApprove: transitions.approve,
  onReject: transitions.reject,
  onInvite: invitation.request,
  onBackToWaiting: transitions.backToWaiting,
  onMoveToTesting: transitions.moveToTesting,
  onMoveToJoined: transitions.moveToJoined,
  onWithdraw: transitions.withdraw,
  onNavigateToEntry: navigateToEntry,
  onDeleteEntry: requestDeleteEntry,
  onSetFields: setFieldsVisible,
  onAddEntry: navigateToCreateEntry,
  onCreateInvite: invite.openModal,
  onDeleteInvite: invite.remove,
  onCopyLink: invite.copyLink,
}))

const {loading, failure} = useAsyncLoader(async () => {
  const [listData, entryData, inviteData, fieldData, groupData] = await Promise.all([
    waitingList.getById(listId.value),
    waitingList.listEntries(listId.value),
    waitingList.listInvites(listId.value),
    waitingList.listFields(listId.value),
    memberGroups.listGroups(),
  ])
  list.value = listData
  entries.value = entryData
  invites.value = inviteData
  fields.value = fieldData
  groups.value = groupData
})

const invite = useListInvites(listId, invites, failure, flash)
const transitions = useEntryTransitions(listId, entries, failure)
const invitation = useEntryInvitation(listId, entries, failure)

/** Shows or hides questions as columns for everybody on this list, written as one change. */
async function setFieldsVisible(fieldIds: number[], visible: boolean) {
  if (!list.value) return
  const current = new Set(list.value.visibleFields ?? [])
  for (const fieldId of fieldIds) {
    if (visible) current.add(fieldId)
    else current.delete(fieldId)
  }
  try {
    list.value = await waitingList.updateVisibleFields(listId.value, [...current])
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}

function navigateToCreateEntry() {
  router.push({ name: 'waiting-list-create-entry', params: { id: listId.value } })
}

const {
  show: showDeleteEntryModal,
  target: deleteEntryTarget,
  request: requestDeleteEntry,
  confirm: confirmDeleteEntry,
} = useConfirmAction<WaitingListEntryWithScore>({
  onConfirm: e => waitingList.deleteEntry(listId.value, e.entry.id),
  onSuccess: async () => {
    entries.value = await waitingList.listEntries(listId.value)
    refreshSidebarCounts()
  },
  failure,
})

function navigateToEntry(entryId: number) {
  router.push({ name: 'waiting-list-entry', params: { id: listId.value, entryId } })
}

function navigateToFields() {
  router.push({ name: 'waiting-list-fields', params: { id: listId.value } })
}

function goBack() {
  router.push({ name: 'waiting-lists' })
}

const { running: deletingList, failure: deleteListFailure, run: confirmDeleteList } = useAsyncAction(async () => {
  failure.value = null
  await waitingList.deleteList(listId.value)
  router.push({ name: 'waiting-lists' })
})

const actionFailure = computed(() =>
  invite.createFailure.value ?? transitions.failure.value ?? invitation.failure.value ?? deleteListFailure.value,
)

function handleListUpdated(updated: WaitingList) {
  list.value = updated
}

function showSuccessMessage(msg: string) {
  flash(msg)
}

function showFailure(reported: Failure) {
  failure.value = reported
}

</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.waiting-list-detail.subtitle')"
  >
    <div class="space-y-6">
      <DetailHeader
        :can-manage="canManage"
        @back="goBack"
        @manage-fields="navigateToFields"
        @delete-list="showDeleteModal = true"
      />

      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure ?? actionFailure"/>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <LoadedSections
        v-if="!loading && list"
        :list="list"
        :list-id="listId"
        :fields="fields"
        :groups="groups"
        :invites="invites"
        :entry-groups="entryGroups"
        :visible-field-ids="visibleFieldIds"
        :permissions="permissions"
        :actions="sectionActions"
      />

      <DetailModals
        :invite="invite"
        :transitions="transitions"
        :invitation="invitation"
        v-model:show-delete="showDeleteModal"
        :list-name="list?.name"
        :deleting-list="deletingList"
        v-model:show-delete-entry="showDeleteEntryModal"
        :delete-entry-target="deleteEntryTarget"
        @confirm-delete-list="confirmDeleteList"
        @confirm-delete-entry="confirmDeleteEntry"
      />
    </div>
  </ViewContent>
</template>
