/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MemberEditTabs from './editview/MemberEditTabs.vue'
import type {MemberEditData} from './editview/types'
import type {ProfileField} from '@/api/profileFields'
import {StationPermission, StationUserType, type MemberGroup, type PermissionGrant, type StationMember, type UserTag} from '@/api/types'
import {profileFields, stationMembers, memberGroups, userTags, inventory} from '@/api'
import type {MyInventoryItem} from '@/api/inventory'
import {decodeProfileValues} from '@/util/profileFields'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const {hasPermission} = useSession()
const route = useRoute()
const router = useRouter()

const memberId = computed(() => Number(route.params.id))

const member = ref<StationMember | null>(null)
const fields = ref<ProfileField[]>([])
const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const editValues = ref<Map<number, string>>(new Map())
const editRoleIds = ref<Set<number>>(new Set())
const editGroupIds = ref<Set<number>>(new Set())
const editTagIds = ref<Set<number>>(new Set())
const typeLockedPermissions = ref<Map<string, string>>(new Map())
const groupLockedPermissions = ref<Map<string, string>>(new Map())
const lockedPermissions = computed(() => new Map([...groupLockedPermissions.value, ...typeLockedPermissions.value]))
const editUserType = ref('')
const memberInventory = ref<MyInventoryItem[]>([])

const allMembers = ref<StationMember[]>([])

const editData = computed<MemberEditData>(() => ({
  fields: fields.value,
  values: editValues.value,
  allRoles: allRoles.value,
  allGroups: allGroups.value,
  allTags: allTags.value,
  allMembers: allMembers.value,
  userType: editUserType.value,
  roleIds: editRoleIds.value,
  groupIds: editGroupIds.value,
  tagIds: editTagIds.value,
  lockedPermissions: lockedPermissions.value,
  memberInventory: memberInventory.value,
}))

async function loadTypePermissions(userType: string) {
  try {
    const effective = await stationMembers.getEffectiveUserTypePermissions(userType)
    typeLockedPermissions.value = new Map(effective.map(p => [p, t('permissions.grantedByUserType')]))
  } catch {
    typeLockedPermissions.value = new Map()
  }
}

async function loadGroupPermissions(groupIds: Set<number>) {
  try {
    const groups = allGroups.value.filter(g => groupIds.has(g.id))
    const grants = await Promise.all(groups.map(g => memberGroups.getGroupPermissions(g.id)))
    const map = new Map<string, string>()
    groups.forEach((group, i) => {
      for (const grant of grants[i] ?? []) {
        if (!map.has(grant.permission)) map.set(grant.permission, t('permissions.grantedByGroup', {name: group.name}))
      }
    })
    groupLockedPermissions.value = map
  } catch {
    groupLockedPermissions.value = new Map()
  }
}

async function onGroupsChanged(groupIds: Set<number>) {
  editGroupIds.value = groupIds
  await loadGroupPermissions(groupIds)
}

async function onUserTypeChanged(userType: string) {
  editUserType.value = userType
  await loadTypePermissions(userType)
  fields.value = await profileFields.getMemberFields(memberId.value)
}

const {loading, error} = useAsyncLoader(async () => {
  const [allFields, allMembers_, roles, memberData, memberPermissions, profileValues, groups, tags, mGroups, mTags] = await Promise.all([
    profileFields.getMemberFields(memberId.value),
    stationMembers.listMembers(),
    stationMembers.listAllPermissions(),
    stationMembers.getMember(memberId.value),
    stationMembers.getPermissions(memberId.value),
    profileFields.getValues(memberId.value),
    memberGroups.listGroups(),
    userTags.listTags(),
    memberGroups.getMemberGroups(memberId.value),
    userTags.getMemberTags(memberId.value),
  ])
  fields.value = allFields
  allMembers.value = allMembers_
  allRoles.value = roles
  allGroups.value = groups
  allTags.value = tags
  editGroupIds.value = new Set(mGroups.map(g => g.id))
  editTagIds.value = new Set(mTags.map(t => t.id))
  member.value = allMembers_.find(m => m.id === memberId.value) ?? null
  editUserType.value = memberData.userType ?? StationUserType.MEMBER
  editRoleIds.value = new Set(memberPermissions.map(r => r.id))
  await Promise.all([loadTypePermissions(editUserType.value), loadGroupPermissions(editGroupIds.value)])

  editValues.value = decodeProfileValues(profileValues)

  if (!hasPermission(StationPermission.INVENTORY_READ)) return
  try {
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch {
    memberInventory.value = []
  }
})

function goBack() {
  router.push({name: 'members-list'})
}
</script>

<template>
  <ViewContent
      :title="t('pages.members-edit.title')"
      :subtitle="t('pages.members-edit.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('memberEdit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ member.name || member.email }}</SectionHeader>

        <MemberEditTabs
            :member="member"
            :member-id="memberId"
            :data="editData"
            @user-type-changed="onUserTypeChanged"
            @groups-changed="onGroupsChanged"
        />
      </template>
    </div>
  </ViewContent>
</template>
