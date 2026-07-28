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
import type {MemberGroup} from '@/api/types'
import { waitingList, memberGroups } from '@/api'
import { StationPermission } from '@/api/types'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useFlashMessage } from '@/composables/useFlashMessage'

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

const showInviteModal = ref(false)
const inviteMaxUses = ref<number | undefined>(undefined)
const inviteExpiresAt = ref('')

const showDeleteModal = ref(false)

type TransitionKind = 'invite' | 'testing' | 'join' | 'approve' | 'reject' | 'withdraw'

interface PendingTransition {
  entry: WaitingListEntryWithScore
  kind: TransitionKind
}

const pendingTransition = ref<PendingTransition | null>(null)

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
  onApprove: doApproveEntry,
  onReject: doRejectEntry,
  onInvite: doInviteEntry,
  onMoveToTesting: doMoveToTesting,
  onMoveToJoined: doMoveToJoined,
  onWithdraw: doWithdrawEntry,
  onNavigateToEntry: navigateToEntry,
  onNavigateToMember: navigateToMember,
  onDeleteEntry: requestDeleteEntry,
  onToggleField: toggleFieldVisibility,
  onToggleFieldMenu: () => { showFieldToggle.value = !showFieldToggle.value },
  onAddEntry: navigateToCreateEntry,
  onCreateInvite: openInviteModal,
  onDeleteInvite: deleteInvite,
  onCopyLink: copyInviteLink,
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

function openInviteModal() {
  inviteMaxUses.value = undefined
  inviteExpiresAt.value = ''
  showInviteModal.value = true
}

const { running: creatingInvite, error: createInviteError, run: createInvite } = useAsyncAction(async () => {
  error.value = ''
  await waitingList.createInvite(listId.value, {
    maxUses: inviteMaxUses.value || undefined,
    expiresAt: inviteExpiresAt.value || undefined,
  })
  invites.value = await waitingList.listInvites(listId.value)
  showInviteModal.value = false
})

async function deleteInvite(inviteId: number) {
  error.value = ''
  try {
    await waitingList.deleteInvite(listId.value, inviteId)
    invites.value = await waitingList.listInvites(listId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function copyInviteLink(code: string) {
  const url = `${window.location.origin}/waiting-list/register?code=${code}`
  await navigator.clipboard.writeText(url)
  flash(t('waitingList.linkCopied'))
}

function navigateToCreateEntry() {
  router.push({ name: 'waiting-list-create-entry', params: { id: listId.value } })
}

function requestTransition(entryId: number, kind: TransitionKind) {
  const entry = entries.value.find(e => e.entry.id === entryId)
  if (!entry) return
  pendingTransition.value = { entry, kind }
}
const doInviteEntry = (id: number) => requestTransition(id, 'invite')
const doMoveToTesting = (id: number) => requestTransition(id, 'testing')
const doMoveToJoined = (id: number) => requestTransition(id, 'join')
const doApproveEntry = (id: number) => requestTransition(id, 'approve')
const doRejectEntry = (id: number) => requestTransition(id, 'reject')
const doWithdrawEntry = (id: number) => requestTransition(id, 'withdraw')

const { running: runningTransition, error: transitionError, run: confirmTransition } = useAsyncAction(async () => {
  if (!pendingTransition.value) return
  const { entry, kind } = pendingTransition.value
  const entryId = entry.entry.id
  error.value = ''
  if (kind === 'invite') {
    await waitingList.inviteEntry(listId.value, entryId)
  } else if (kind === 'testing') {
    await waitingList.moveToTesting(listId.value, entryId)
  } else if (kind === 'join') {
    const result = await waitingList.moveToJoined(listId.value, entryId)
    refreshSidebarCounts()
    pendingTransition.value = null
    if (result.memberId && hasPermission(StationPermission.MEMBER_EDIT)) {
      router.push({ name: 'members-edit', params: { id: result.memberId } })
      return
    }
  } else if (kind === 'approve') {
    await waitingList.approveEntry(listId.value, entryId)
  } else if (kind === 'reject') {
    await waitingList.rejectEntry(listId.value, entryId)
  } else if (kind === 'withdraw') {
    await waitingList.withdrawEntry(listId.value, entryId)
  }
  entries.value = await waitingList.listEntries(listId.value)
  refreshSidebarCounts()
  pendingTransition.value = null
})

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
  createInviteError.value || transitionError.value || deleteListError.value,
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
        v-model:show-invite="showInviteModal"
        v-model:invite-max-uses="inviteMaxUses"
        v-model:invite-expires-at="inviteExpiresAt"
        :creating-invite="creatingInvite"
        v-model:show-delete="showDeleteModal"
        :list-name="list?.name"
        :deleting-list="deletingList"
        :pending-transition="pendingTransition"
        :running-transition="runningTransition"
        v-model:show-delete-entry="showDeleteEntryModal"
        :delete-entry-target="deleteEntryTarget"
        @submit-invite="createInvite"
        @confirm-delete-list="confirmDeleteList"
        @cancel-transition="pendingTransition = null"
        @confirm-transition="confirmTransition"
        @confirm-delete-entry="confirmDeleteEntry"
      />
    </div>
  </ViewContent>
</template>
