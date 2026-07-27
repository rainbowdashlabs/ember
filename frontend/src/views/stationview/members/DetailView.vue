/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import DetailModals from './detailview/DetailModals.vue'
import DetailHeader from './detailview/DetailHeader.vue'
import LoadedTabs from './detailview/LoadedTabs.vue'
import type { ProfileField, ProfileFieldChange, StationMember } from '@/api/types'
import { StationPermission, StationUserType, ExchangeStatus, StationModules } from '@/api/types'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/types'
import { profileFields, profileFieldChanges, stationMembers, members, inventory, exchanges, memberGroups, userTags } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useAsyncAction } from '@/composables/useAsyncAction'

const { t } = useI18n()
const route = useRoute()

const { sessionInfo, hasPermission, canManageMembers, isGuardian, canManageInventory, isModuleEnabled } = useSession()
const canEdit = computed(() => hasPermission(StationPermission.MEMBER_EDIT))
const inventoryEnabled = computed(() => isModuleEnabled(StationModules.INVENTORY))
const canReadInventory = computed(() => inventoryEnabled.value && hasPermission(StationPermission.INVENTORY_READ))
const showInventoryManagement = computed(() => canReadInventory.value && canManageInventory())

const memberId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)
const showChangeHistory = computed(() => hasPermission(StationPermission.MEMBER_CHANGES) || isGuardian())

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())
const memberUserType = ref<string>('')
const managers = ref<StationMember[]>([])
const managerValues = ref<Map<number, Map<number, string>>>(new Map())
const managerUserTypes = ref<Map<number, string>>(new Map())
const allMembers = ref<StationMember[]>([])
const changes = ref<ProfileFieldChange[]>([])
const memberInventory = ref<MyInventoryItem[]>([])
const memberExchanges = ref<ExchangeRequestEntry[]>([])
const memberPermissions = ref<import('@/api/types').PermissionGrant[]>([])
const memberGroupList = ref<import('@/api/types').MemberGroup[]>([])
const memberTagList = ref<import('@/api/types').UserTag[]>([])
const formerSuccess = ref(false)
const deleteSuccess = ref(false)

const exchangeSizes = ref<import('@/api/types').InventorySize[]>([])

const activeTab = ref('profile')

const tabs = computed(() => {
  const t_ = [
    { key: 'profile', label: t('memberDetail.tabProfile') },
    { key: 'permissions', label: t('memberDetail.tabPermissions') },
    { key: 'guardians', label: t('memberDetail.tabGuardians') },
  ]
  if (canEdit.value) {
    t_.push({ key: 'absences', label: t('memberDetail.tabAbsences') })
  }
  if (canReadInventory.value) {
    t_.push({ key: 'inventory', label: t('memberDetail.tabInventory') })
  }
  if (hasPermission(StationPermission.MEMBER_NOTES)) {
    t_.push({ key: 'notes', label: t('memberDetail.tabNotes') })
  }
  return t_
})

const modalsRef = ref<InstanceType<typeof DetailModals> | null>(null)

function getScopeForUserType(ut: string): string {
  if (ut === StationUserType.MEMBER || ut === StationUserType.TRIAL) return 'MEMBER'
  if (ut === StationUserType.GUARDIAN) return 'GUARDIAN'
  if (ut === StationUserType.TEAM) return 'TEAM'
  if (ut === StationUserType.MANAGER) return 'MANAGER'
  return 'MEMBER'
}

const applicableFields = computed(() => {
  const scope = getScopeForUserType(memberUserType.value)
  return fields.value.filter(f => f.scope === scope)
})

const availableManagers = computed(() => {
  const managerIds = new Set(managers.value.map(m => m.id))
  managerIds.add(memberId.value)
  return allMembers.value
    .filter(m => !managerIds.has(m.id))
    .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

const showManagerSection = computed(() =>
  memberUserType.value === StationUserType.MEMBER || memberUserType.value === StationUserType.TRIAL
)

const managerUserTypesAsRoleMap = computed(() => {
  const result = new Map<number, string[]>()
  for (const [id, ut] of managerUserTypes.value) {
    result.set(id, ut ? [ut] : [])
  }
  return result
})

const formerBlockReasons = computed(() => {
  const reasons: string[] = []
  if (memberInventory.value.length > 0) {
    reasons.push(t('memberDetail.formerBlockInventory', { count: memberInventory.value.length }))
  }
  const forbidden = [StationUserType.GUARDIAN, StationUserType.MANAGER]
  if (forbidden.includes(memberUserType.value as any)) {
    reasons.push(t('memberDetail.formerBlockRole'))
  }
  return reasons
})
const canMarkFormer = computed(() => formerBlockReasons.value.length === 0 && !!member.value)

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function getFieldValue(fieldId: number): unknown {
  const raw = values.value.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFieldValue(mgrId: number, fieldId: number): unknown {
  const vals = managerValues.value.get(mgrId)
  if (!vals) return ''
  const raw = vals.get(fieldId) ?? ''
  try { return JSON.parse(raw) } catch { return raw }
}

function getManagerFields(mgrId: number): ProfileField[] {
  const ut = managerUserTypes.value.get(mgrId) ?? ''
  const scope = getScopeForUserType(ut)
  return fields.value.filter(f => f.scope === scope)
}

async function loadManagerDetails(mgrs: StationMember[]) {
  const mgrVals = new Map<number, Map<number, string>>()
  const mgrTypes = new Map<number, string>()
  for (const mgr of mgrs) {
    try {
      const [vals, memberData] = await Promise.all([
        profileFields.getValues(mgr.id),
        stationMembers.getMember(mgr.id),
      ])
      const fieldMap = new Map<number, string>()
      for (const v of vals) { fieldMap.set(v.fieldId, v.value ?? '') }
      mgrVals.set(mgr.id, fieldMap)
      mgrTypes.set(mgr.id, (memberData as any).userType ?? '')
    } catch { /* skip */ }
  }
  managerValues.value = mgrVals
  managerUserTypes.value = mgrTypes
}

const {loading, error} = useAsyncLoader(async () => {
  const [allFields, allMems, memberData, profileValues, mgrs, perms, mGroups, mTags] = await Promise.all([
    profileFields.listFields(),
    stationMembers.listMembers(),
    stationMembers.getMember(memberId.value),
    profileFields.getValues(memberId.value),
    stationMembers.getManagers(memberId.value),
    stationMembers.getPermissions(memberId.value),
    memberGroups.getMemberGroups(memberId.value),
    userTags.getMemberTags(memberId.value),
  ])
  fields.value = allFields
  allMembers.value = allMems
  member.value = allMems.find(m => m.id === memberId.value) ?? null
  memberUserType.value = (memberData as any).userType ?? ''
  managers.value = mgrs
  memberPermissions.value = perms
  memberGroupList.value = mGroups
  memberTagList.value = mTags
  const map = new Map<number, string>()
  for (const v of profileValues) { map.set(v.fieldId, v.value ?? '') }
  values.value = map
  await loadManagerDetails(mgrs)
  if (showChangeHistory.value) {
    try { changes.value = await profileFieldChanges.getChanges(memberId.value) } catch { /* ignore */ }
  }
  if (canReadInventory.value) {
    try { memberInventory.value = await inventory.memberItems(memberId.value) } catch { memberInventory.value = [] }
    try {
      const allExch = await exchanges.listExchanges()
      memberExchanges.value = allExch.filter(e => e.memberId === memberId.value && e.status !== ExchangeStatus.DONE)
    } catch { memberExchanges.value = [] }
  }
})

async function handleLinkManager(managerId: number) {
  error.value = ''
  try {
    const currentIds = managers.value.map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, managerId] })
    managers.value = await stationMembers.getManagers(memberId.value)
    await loadManagerDetails(managers.value)
  } catch { error.value = t('common.error') }
}

async function handleRemoveManager(mgrId: number) {
  error.value = ''
  try {
    const newIds = managers.value.filter(m => m.id !== mgrId).map(m => m.id)
    await stationMembers.setManagers(memberId.value, { managerIds: newIds })
    managers.value = await stationMembers.getManagers(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleCreateManager(data: { firstName: string; lastName: string; email: string }) {
  error.value = ''
  try {
    const invited = await members.invite({ email: data.email, firstName: data.firstName, lastName: data.lastName })
    const updatedMembers = await stationMembers.listMembers()
    const newMember = updatedMembers.find(m => m.accountId === invited.id)
    if (newMember) {
      const currentIds = managers.value.map(m => m.id)
      await stationMembers.setManagers(memberId.value, { managerIds: [...currentIds, newMember.id] })
      managers.value = await stationMembers.getManagers(memberId.value)
      await loadManagerDetails(managers.value)
      allMembers.value = updatedMembers
    }
  } catch { error.value = t('common.error') }
}

const { running: markingFormer, error: formerError, run: handleMarkFormer } = useAsyncAction(async () => {
  error.value = ''
  await stationMembers.markFormer(memberId.value)
  formerSuccess.value = true
}, { formatError: () => t('common.error') })

const { running: deletingMember, error: deleteError, run: handleDeleteMember } = useAsyncAction(async () => {
  error.value = ''
  await stationMembers.deleteMember(memberId.value)
  deleteSuccess.value = true
}, { formatError: () => t('common.error') })

async function handleAssignItem(itemId: number) {
  error.value = ''
  try {
    await inventory.assignItem(itemId, { memberId: memberId.value })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleUnassignItem(item: MyInventoryItem) {
  error.value = ''
  try {
    await inventory.assignItem(item.id, { memberId: null })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleReassignItem(itemId: number, targetMemberId: number) {
  error.value = ''
  try {
    await inventory.assignItem(itemId, { memberId: targetMemberId })
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch { error.value = t('common.error') }
}

async function handleSubmitExchange(data: { item: MyInventoryItem; newSizeId?: number; reason: string }) {
  error.value = ''
  try {
    await exchanges.createExchange({
      memberId: memberId.value,
      itemId: data.item.id,
      inventoryId: data.item.inventoryId,
      oldSizeId: data.item.sizeId ?? undefined,
      newSizeId: data.newSizeId,
      reason: data.reason,
    })
  } catch { error.value = t('common.error') }
}

async function handleLoadExchangeSizes(inventoryId: number) {
  try { exchangeSizes.value = await inventory.listSizes(inventoryId) } catch { exchangeSizes.value = [] }
}

async function loadChanges() {
  try { changes.value = await profileFieldChanges.getChanges(memberId.value) } catch { /* ignore */ }
}

const loadedTabsProps = computed(() => ({
  member: member.value!,
  memberId: memberId.value,
  currentMemberId: currentMemberId.value,
  tabs: tabs.value,
  applicableFields: applicableFields.value,
  changes: changes.value,
  showChangeHistory: showChangeHistory.value,
  getFieldValue,
  memberUserType: memberUserType.value,
  memberPermissions: memberPermissions.value,
  memberGroupList: memberGroupList.value,
  memberTagList: memberTagList.value,
  showManagerSection: showManagerSection.value,
  managers: managers.value,
  availableManagers: availableManagers.value,
  managerValues: managerValues.value,
  managerUserTypesAsRoleMap: managerUserTypesAsRoleMap.value,
  fields: fields.value,
  canEdit: canEdit.value,
  memberDisplayName,
  getManagerFields,
  getManagerFieldValue,
  memberInventory: memberInventory.value,
  memberExchanges: memberExchanges.value,
  showInventoryManagement: showInventoryManagement.value,
  canManageInventory: canManageInventory(),
}))

const detailModalsProps = computed(() => ({
  memberDisplayName: member.value ? memberDisplayName(member.value) : '',
  canMarkFormer: canMarkFormer.value,
  formerBlockReasons: formerBlockReasons.value,
  markingFormer: markingFormer.value,
  deletingMember: deletingMember.value,
  allMembers: allMembers.value,
  memberId: memberId.value,
  memberDisplayNameFn: memberDisplayName,
  exchangeSizes: exchangeSizes.value,
}))

</script>

<template>
  <ViewContent
      :title="t('pages.members-detail.title')"
      :subtitle="t('pages.members-detail.subtitle')"
  >
    <div class="space-y-6">
      <DetailHeader
        :member-id="memberId" :can-edit="canEdit"
        :former-success="formerSuccess" :delete-success="deleteSuccess"
        @open-former-modal="modalsRef?.openFormerModal()"
        @open-delete-modal="modalsRef?.openDeleteModal()"
      />

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error || formerError || deleteError" variant="error">{{ error || formerError || deleteError }}</Alert>

      <LoadedTabs
        v-if="!loading && member"
        v-bind="loadedTabsProps"
        @reload-changes="loadChanges"
        @link-manager="handleLinkManager"
        @remove-manager="handleRemoveManager"
        @create-manager="handleCreateManager"
        @assign-item="modalsRef?.openAssignModal()"
        @request-exchange="modalsRef?.openExchangeModal($event)"
        @unassign="handleUnassignItem"
        @reassign="modalsRef?.openReassignModal($event)"
      />

      <Alert v-if="formerSuccess" variant="success">{{ t('memberDetail.formerSuccess') }}</Alert>
      <Alert v-if="deleteSuccess" variant="success">{{ t('memberDetail.deleteSuccess') }}</Alert>

      <DetailModals
        ref="modalsRef"
        v-bind="detailModalsProps"
        @mark-former="handleMarkFormer"
        @delete-member="handleDeleteMember"
        @assign-item="handleAssignItem"
        @reassign-item="handleReassignItem"
        @submit-exchange="handleSubmitExchange"
        @load-exchange-sizes="handleLoadExchangeSizes"
      />
    </div>
  </ViewContent>
</template>
