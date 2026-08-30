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
import { useMemberProfileFields } from './detailview/useMemberProfileFields'
import { useMemberManagers } from './detailview/useMemberManagers'
import { useMemberInventory } from './detailview/useMemberInventory'
import { useMemberLifecycle } from './detailview/useMemberLifecycle'
import { memberDisplayName } from './listview/useMemberData'
import type { ProfileFieldChange } from '@/api/profileFieldChanges'
import {StationModules, StationPermission, StationUserType, type MemberGroup, type PermissionGrant, type StationMember, type UserTag} from '@/api/types'
import { memberGroups, profileFieldChanges, profileFields, stationMembers, userTags } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()

const { sessionInfo, hasPermission, isGuardian, canManageInventory, isModuleEnabled } = useSession()
const canEdit = computed(() => hasPermission(StationPermission.MEMBER_EDIT))
const inventoryEnabled = computed(() => isModuleEnabled(StationModules.INVENTORY))
const canReadInventory = computed(() => inventoryEnabled.value && hasPermission(StationPermission.INVENTORY_READ))
const showInventoryManagement = computed(() => canReadInventory.value && canManageInventory())

const memberId = computed(() => Number(route.params.id))
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)
const showChangeHistory = computed(() => hasPermission(StationPermission.MEMBER_CHANGES) || isGuardian())

const member = ref<StationMember | null>(null)
const memberUserType = ref<string>('')
const allMembers = ref<StationMember[]>([])
const changes = ref<ProfileFieldChange[]>([])
const memberPermissions = ref<PermissionGrant[]>([])
const memberGroupList = ref<MemberGroup[]>([])
const memberTagList = ref<UserTag[]>([])

const modalsRef = ref<InstanceType<typeof DetailModals> | null>(null)

const { loading, error } = useAsyncLoader(loadDetail)

const { fields, applicableFields, fieldsForUserType, getFieldValue, setValues } = useMemberProfileFields(memberUserType)

const {
  managers,
  managerValues,
  managerUserTypesAsRoleMap,
  availableManagers,
  getManagerFields,
  getManagerFieldValue,
  loadDetails: loadManagerDetails,
  linkManager,
  removeManager,
  createManager,
} = useMemberManagers(memberId, allMembers, fieldsForUserType, error)

const {
  items: memberInventory,
  exchangeRequests: memberExchanges,
  exchangeSizes,
  requirements: memberRequirements,
  load: loadMemberInventory,
  assignItem,
  handOutNewItem,
  unassignItem,
  reassignItem,
  submitExchange,
  loadExchangeSizes,
} = useMemberInventory(memberId, error)

const {
  formerSuccess,
  deleteSuccess,
  formerBlockReasons,
  canMarkFormer,
  markingFormer,
  formerError,
  markFormer,
  deletingMember,
  deleteError,
  deleteMember,
} = useMemberLifecycle(memberId, member, memberUserType, memberInventory, error)

const tabs = computed(() => {
  const t_ = [
    { key: 'profile', label: t('memberDetail.tabProfile') },
    { key: 'permissions', label: t('memberDetail.tabPermissions') },
    { key: 'guardians', label: t('memberDetail.tabGuardians') },
    { key: 'documents', label: t('memberDetail.tabDocuments') },
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

const showManagerSection = computed(() =>
  memberUserType.value === StationUserType.MEMBER || memberUserType.value === StationUserType.TRIAL
)

async function loadChanges() {
  try { changes.value = await profileFieldChanges.getChanges(memberId.value) } catch { void 0 }
}

async function loadDetail() {
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
  memberUserType.value = memberData.userType ?? ''
  managers.value = mgrs
  memberPermissions.value = perms
  memberGroupList.value = mGroups
  memberTagList.value = mTags
  setValues(profileValues)
  await loadManagerDetails(mgrs)
  if (showChangeHistory.value) {
    await loadChanges()
  }
  if (canReadInventory.value) {
    await loadMemberInventory()
  }
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
  allMembers: allMembers.value,
  managerValues: managerValues.value,
  managerUserTypesAsRoleMap: managerUserTypesAsRoleMap.value,
  fields: fields.value,
  canEdit: canEdit.value,
  memberDisplayName,
  getManagerFields,
  getManagerFieldValue,
  memberInventory: memberInventory.value,
  memberRequirements: memberRequirements.value,
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
        @link-manager="linkManager"
        @remove-manager="removeManager"
        @create-manager="createManager"
        @assign-item="modalsRef?.openAssignModal()"
        @hand-out="assignItem"
        @hand-out-new="handOutNewItem"
        @request-exchange="modalsRef?.openExchangeModal($event)"
        @unassign="unassignItem"
        @reassign="modalsRef?.openReassignModal($event)"
      />

      <Alert v-if="formerSuccess" variant="success">{{ t('memberDetail.formerSuccess') }}</Alert>
      <Alert v-if="deleteSuccess" variant="success">{{ t('memberDetail.deleteSuccess') }}</Alert>

      <DetailModals
        ref="modalsRef"
        v-bind="detailModalsProps"
        @mark-former="markFormer"
        @delete-member="deleteMember"
        @assign-item="assignItem"
        @reassign-item="reassignItem"
        @submit-exchange="submitExchange"
        @load-exchange-sizes="loadExchangeSizes"
      />
    </div>
  </ViewContent>
</template>
