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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DetailModals from './detailview/DetailModals.vue'
import DetailHeader from './detailview/DetailHeader.vue'
import LoadedTabs from './detailview/LoadedTabs.vue'
import { useMemberProfileFields } from './detailview/useMemberProfileFields'
import { useMemberManagers } from './relations/useMemberManagers'
import { useManagedMembers } from './relations/useManagedMembers'
import { useMemberInventory } from './detailview/useMemberInventory'
import { useMemberLifecycle } from './detailview/useMemberLifecycle'
import { memberDisplayName } from './listview/useMemberData'
import type { ProfileFieldChange } from '@/api/profileFieldChanges'
import {StationModules, StationPermission, StationUserType, type MemberGroup, type PermissionGrant, type StationMember, type UserTag} from '@/api/types'
import { memberGroups, profileFieldChanges, profileFields, stationMembers, userTags } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { describeFailure } from '@/util/failure'

const { t } = useI18n()
const route = useRoute()

const { sessionInfo, hasPermission, isGuardian, canManageInventory, isModuleEnabled, canEditMemberAccounts } = useSession()
const canEdit = computed(() => canEditMemberAccounts())
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

const { loading, failure } = useAsyncLoader(loadDetail)

const {
  fields, applicableFields, fieldsForUserType, getFieldValue, setValues, loadAudiences,
} = useMemberProfileFields(memberUserType)

const {
  managers,
  availableManagers,
  getManagerFields,
  getManagerFieldValue,
  loadDetails: loadManagerDetails,
  linkManager,
  removeManager,
  createManager,
} = useMemberManagers(memberId, allMembers, fieldsForUserType, failure)

const {managedMembers, availableManaged, linkManaged, removeManaged} =
    useManagedMembers(memberId, allMembers, failure)

const {
  items: memberInventory,
  requirements: memberRequirements,
  load: loadMemberInventory,
  assignItem,
  planHandOut,
  handOutNewItem,
  unassignItem,
  reassignItem,
} = useMemberInventory(memberId, failure)

const {
  formerSuccess,
  deleteSuccess,
  formerBlockReasons,
  canMarkFormer,
  markingFormer,
  formerFailure,
  markFormer,
  deletingMember,
  deleteFailure,
  deleteMember,
} = useMemberLifecycle(memberId, member, memberUserType, memberInventory, failure)

const tabs = computed(() => {
  const t_ = [
    { key: 'profile', label: t('memberDetail.tabProfile') },
    { key: 'permissions', label: t('memberDetail.tabPermissions') },
    { key: 'relations', label: relationsTabLabel.value },
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

const showGuardians = computed(() =>
  memberUserType.value === StationUserType.MEMBER || memberUserType.value === StationUserType.TRIAL
)

/**
 * Whether this member looks after somebody else, which is what the tab is about for a guardian:
 * asking who their own guardians are says nothing, and the answer was always the same empty line.
 */
const managesMembers = computed(() =>
  managedMembers.value.length > 0 || memberUserType.value === StationUserType.GUARDIAN
)

const relationsTabLabel = computed(() =>
  managesMembers.value ? t('memberDetail.tabManagedMembers') : t('memberDetail.tabGuardians')
)

/**
 * The person's own name at the head of the page, and nothing besides: it is what makes the tab,
 * the history and a bookmark worth reading. The word "Mitglied" stands there until the member has
 * arrived, and where they could not be fetched at all.
 */
const pageTitle = computed(() =>
  member.value ? memberDisplayName(member.value) : t('pages.members-detail.title'))

/**
 * Reads the record of what has been changed about this member.
 *
 * <p>Said out loud when it fails, because an empty history reads as a profile nobody has ever touched,
 * which on a page whose whole point is who changed what is the opposite of no answer.
 */
async function loadChanges() {
  try {
    changes.value = await profileFieldChanges.getChanges(memberId.value)
  } catch (e) {
    changes.value = []
    failure.value = {...describeFailure(e, t), message: t('memberDetail.changesUnreadable')}
  }
}

async function loadDetail() {
  const [allFields, allMems, memberData, profileValues, mgrs, managed, perms, mGroups, mTags] = await Promise.all([
    profileFields.listFields(),
    stationMembers.listMembers(),
    stationMembers.getMember(memberId.value),
    profileFields.getValues(memberId.value),
    stationMembers.getManagers(memberId.value),
    stationMembers.getManaged(memberId.value),
    stationMembers.getPermissions(memberId.value),
    memberGroups.getMemberGroups(memberId.value),
    userTags.getMemberTags(memberId.value),
    loadAudiences(),
  ])
  fields.value = allFields
  allMembers.value = allMems
  member.value = allMems.find(m => m.id === memberId.value) ?? null
  memberUserType.value = memberData.userType ?? ''
  managers.value = mgrs
  managedMembers.value = managed
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
  showGuardians: showGuardians.value,
  showManaged: managesMembers.value,
  managers: managers.value,
  managedMembers: managedMembers.value,
  availableManagers: availableManagers.value,
  availableManaged: availableManaged.value,
  allMembers: allMembers.value,
  fields: fields.value,
  canEdit: canEdit.value,
  memberDisplayName,
  getManagerFields,
  getManagerFieldValue,
  memberInventory: memberInventory.value,
  memberRequirements: memberRequirements.value,
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
}))

</script>

<template>
  <ViewContent
      :title="pageTitle"
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
      <FailureAlert :failure="failure ?? formerFailure ?? deleteFailure"/>

      <LoadedTabs
        v-if="!loading && member"
        v-bind="loadedTabsProps"
        @reload-changes="loadChanges"
        @link-manager="linkManager"
        @remove-manager="removeManager"
        @create-manager="createManager"
        @link-managed="linkManaged"
        @remove-managed="removeManaged"
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
        @plan-hand-out="planHandOut"
        @reassign-item="reassignItem"
        @exchange-started="loadMemberInventory"
      />
    </div>
  </ViewContent>
</template>
