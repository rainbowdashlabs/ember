/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import GeneralTab from './GeneralTab.vue'
import ProfileTab from './ProfileTab.vue'
import RelationsTab from './RelationsTab.vue'
import NotesTab from './NotesTab.vue'
import type {MemberEditData} from './types'
import type {StationMember} from '@/api/types'

defineProps<{
  member: StationMember
  memberId: number
  data: MemberEditData
}>()

const emit = defineEmits<{
  (e: 'userTypeChanged', userType: string): void
  (e: 'groupsChanged', groupIds: Set<number>): void
}>()

const {t} = useI18n()

const activeTab = ref('profile')

const tabs = computed(() => [
  {key: 'profile', label: t('memberEdit.tabProfile')},
  {key: 'permissions', label: t('memberEdit.tabPermissions')},
  {key: 'relations', label: t('memberEdit.tabRelations')},
  {key: 'notes', label: t('memberEdit.tabNotes')},
])
</script>

<template>
  <TabBar v-model="activeTab" :tabs="tabs"/>

  <ProfileTab
      v-if="activeTab === 'profile'"
      :member="member"
      :member-id="memberId"
      :fields="data.fields"
      :initial-values="data.values"
  />

  <GeneralTab
      v-if="activeTab === 'permissions'"
      :member="member"
      :member-id="memberId"
      :all-roles="data.allRoles"
      :all-groups="data.allGroups"
      :all-tags="data.allTags"
      :initial-user-type="data.userType"
      :initial-role-ids="data.roleIds"
      :initial-group-ids="data.groupIds"
      :initial-tag-ids="data.tagIds"
      :locked-permissions="data.lockedPermissions"
      :member-inventory="data.memberInventory"
      @user-type-changed="v => emit('userTypeChanged', v)"
      @groups-changed="v => emit('groupsChanged', v)"
  />

  <RelationsTab
      v-if="activeTab === 'relations'"
      :member-id="memberId"
      :user-type="data.userType"
      :all-members="data.allMembers"
  />

  <NotesTab v-if="activeTab === 'notes'" :member-id="memberId"/>
</template>
