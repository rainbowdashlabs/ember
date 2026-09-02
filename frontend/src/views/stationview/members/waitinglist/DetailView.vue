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
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useFlashMessage } from '@/composables/useFlashMessage'
import { useListInvites } from './detailview/useListInvites'
import { useEntryTransitions } from './detailview/useEntryTransitions'
import { useEntryInvitation } from './detailview/useEntryInvitation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { isMobile } = useBreakpoint()
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
const showFieldToggle = ref(false)

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
  onError: showErrorMessage,
  onSuccess: showSuccessMessage,
  onApprove: transitions.approve,
  onReject: transitions.reject,
  onInvite: invitation.request,
  onBackToWaiting: transitions.backToWaiting,
  onMoveToTesting: transitions.moveToTesting,
  onMoveToJoined: transitions.moveToJoined,
  onWithdraw: transitions.withdraw,
  onNavigateToEntry: navigateToEntry,
  onNavigateToMember: navigateToMember,
  onDeleteEntry: requestDeleteEntry,
  onToggleField: toggleFieldVisibility,
  onToggleFieldMenu: () => { showFieldToggle.value = !showFieldToggle.value },
  onAddEntry: navigateToCreateEntry,
  onCreateInvite: invite.openModal,
  onDeleteInvite: invite.remove,
  onCopyLink: invite.copyLink,
}))

const {loading, error} = useAsyncLoader(async () => {
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

const invite = useListInvites(listId, invites, error, flash)
const transitions = useEntryTransitions(listId, entries, error)
const invitation = useEntryInvitation(listId, entries, error)

async function toggleFieldVisibility(fieldId: number) {
  if (!list.value) return
  const current = new Set(list.value.visibleFields ?? [])
  if (current.has(fieldId)) current.delete(fieldId)
  else current.add(fieldId)
  try {
    list.value = await waitingList.updateVisibleFields(listId.value, [...current])
  } catch {
    error.value = t('common.error')
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
  error,
})

function navigateToEntry(entryId: number) {
  router.push({ name: 'waiting-list-entry', params: { id: listId.value, entryId } })
}

function navigateToMember(memberId: number) {
  router.push({ name: 'members-detail', params: { id: memberId } })
}

function navigateToFields() {
  router.push({ name: 'waiting-list-fields', params: { id: listId.value } })
}

function goBack() {
  router.push({ name: 'waiting-lists' })
}

const { running: deletingList, error: deleteListError, run: confirmDeleteList } = useAsyncAction(async () => {
  error.value = ''
  await waitingList.deleteList(listId.value)
  router.push({ name: 'waiting-lists' })
})

const actionError = computed(() =>
  invite.createError.value || transitions.error.value || invitation.error.value || deleteListError.value,
)

function handleListUpdated(updated: WaitingList) {
  list.value = updated
}

function showSuccessMessage(msg: string) {
  flash(msg)
}

function showErrorMessage(msg: string) {
  error.value = msg
}

</script>

<template>
  <ViewContent
      :title="t('pages.waiting-list-detail.title')"
      :subtitle="t('pages.waiting-list-detail.subtitle')"
  >
    <div class="space-y-6">
      <DetailHeader
        :can-manage="canManage"
        :is-mobile="isMobile"
        @back="goBack"
        @manage-fields="navigateToFields"
        @delete-list="showDeleteModal = true"
      />

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error || actionError" variant="error">{{ error || actionError }}</Alert>
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
        :is-mobile="isMobile"
        :show-field-toggle="showFieldToggle"
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
