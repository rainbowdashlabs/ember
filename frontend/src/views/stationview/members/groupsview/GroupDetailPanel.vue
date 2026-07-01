/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import GroupMembersTab from './GroupMembersTab.vue'
import type {MemberGroup, PermissionGrant, StationMember} from '@/api/types'
import {useModelProxy} from '@/composables/useModelProxy'

const {t} = useI18n()

const props = defineProps<{
  selectedGroup: MemberGroup
  groupLoading: boolean
  sortedGroupMembers: StationMember[]
  availableMembers: StationMember[]
  groupRoles: PermissionGrant[]
  allRoles: PermissionGrant[]
  groupRoleIds: Set<number>
  canEditRoles: boolean
}>()

const emit = defineEmits<{
  (e: 'add-member', member: StationMember): void
  (e: 'remove-member', member: StationMember): void
  (e: 'update:groupRoleIds', ids: Set<number>): void
}>()

const detailTab = ref('members')
const detailTabs = computed(() => [
  {key: 'members', label: t('memberGroups.tabMembers')},
  {key: 'permissions', label: t('memberGroups.tabPermissions')},
])

const roleIdsModel = useModelProxy(() => props.groupRoleIds, emit, 'groupRoleIds')
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ selectedGroup.name }}</SubHeader>

    <Spinner v-if="groupLoading" size="md"/>

    <template v-if="!groupLoading">
      <TabBar v-model="detailTab" :tabs="detailTabs"/>

      <template v-if="detailTab === 'members'">
        <GroupMembersTab
            :sorted-group-members="sortedGroupMembers"
            :available-members="availableMembers"
            @add="(m) => emit('add-member', m)"
            @remove="(m) => emit('remove-member', m)"
        />
      </template>

      <template v-if="detailTab === 'permissions'">
        <div v-if="canEditRoles" class="space-y-2">
          <PermissionPicker v-model="roleIdsModel" :all-roles="allRoles"/>
          <MutedText v-if="groupRoles.length === 0" size="sm">{{ t('memberGroups.noPermissions') }}</MutedText>
        </div>
        <MutedText v-else size="sm">{{ t('memberGroups.noPermissionAccess') }}</MutedText>
      </template>
    </template>
  </div>
</template>
