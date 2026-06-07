/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import GeneralTab from './editview/GeneralTab.vue'
import ProfileTab from './editview/ProfileTab.vue'
import RelationsTab from './editview/RelationsTab.vue'
import type {ProfileField, StationMember, PermissionGrant, MemberGroup, UserTag} from '@/api/types'
import {StationPermission, StationUserType} from '@/api/types'
import {profileFields, stationMembers, memberGroups, userTags, inventory} from '@/api'
import type {MyInventoryItem} from '@/api/inventory'
import {useSession} from '@/composables/useSession'

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
const typeGrantedPermissions = ref<Set<string>>(new Set())
const editUserType = ref('')
const memberInventory = ref<MyInventoryItem[]>([])
const loading = ref(true)
const error = ref('')
const activeTab = ref('profile')

const allMembers = ref<StationMember[]>([])

const tabs = computed(() => [
  {key: 'profile', label: t('memberEdit.tabProfile')},
  {key: 'permissions', label: t('memberEdit.tabPermissions')},
  {key: 'relations', label: t('memberEdit.tabRelations')},
  {key: 'notes', label: t('memberEdit.tabNotes')},
])

async function loadTypePermissions(userType: string) {
  try {
    const effective = await stationMembers.getEffectiveUserTypePermissions(userType)
    // If the type grants STATION_ADMINISTRATOR, don't hide all children —
    // only hide the top-level grant itself so the picker remains useful
    if (effective.includes('STATION_ADMINISTRATOR')) {
      typeGrantedPermissions.value = new Set(['STATION_ADMINISTRATOR'])
    } else {
      typeGrantedPermissions.value = new Set(effective)
    }
  } catch {
    typeGrantedPermissions.value = new Set()
  }
}

async function onUserTypeChanged(userType: string) {
  editUserType.value = userType
  await loadTypePermissions(userType)
  fields.value = await profileFields.getMemberFields(memberId.value)
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
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
    await loadTypePermissions(editUserType.value)

    const map = new Map<number, string>()
    for (const v of profileValues) {
      let val = v.value ?? ''
      try { val = JSON.parse(val) } catch { /* use as-is */ }
      map.set(v.fieldId, typeof val === 'string' ? val : String(val))
    }
    editValues.value = map
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({name: 'members-list'})
}

onMounted(async () => {
  await loadData()
  if (!hasPermission(StationPermission.INVENTORY_READ)) return
  try {
    memberInventory.value = await inventory.memberItems(memberId.value)
  } catch {
    memberInventory.value = []
  }
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
        {{ t('memberEdit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && member">
        <SectionHeader>{{ member.name || member.email }}</SectionHeader>

        <TabBar v-model="activeTab" :tabs="tabs"/>

        <ProfileTab
            v-if="activeTab === 'profile'"
            :member="member"
            :member-id="memberId"
            :fields="fields"
            :initial-values="editValues"
        />

        <GeneralTab
            v-if="activeTab === 'permissions'"
            :member="member"
            :member-id="memberId"
            :all-roles="allRoles"
            :all-groups="allGroups"
            :all-tags="allTags"
            :initial-user-type="editUserType"
            :initial-role-ids="editRoleIds"
            :initial-group-ids="editGroupIds"
            :initial-tag-ids="editTagIds"
            :type-granted-permissions="typeGrantedPermissions"
            :member-inventory="memberInventory"
            @user-type-changed="onUserTypeChanged"
        />

        <RelationsTab
            v-if="activeTab === 'relations'"
            :member-id="memberId"
            :user-type="editUserType"
            :all-members="allMembers"
        />

        <NeutralContainer v-if="activeTab === 'notes'">
          <NoteEditor :entity-type="'MEMBER'" :entity-id="memberId"/>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
