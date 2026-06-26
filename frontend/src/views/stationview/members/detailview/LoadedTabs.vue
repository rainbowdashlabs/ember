/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import NoteEditor from '@/components/comment/NoteEditor.vue'
import AbsencesTab from './AbsencesTab.vue'
import ProfileTab from './ProfileTab.vue'
import PermissionsTab from './PermissionsTab.vue'
import GuardiansTab from './GuardiansTab.vue'
import InventoryTab from './InventoryTab.vue'
import type { ProfileField, ProfileFieldChange, StationMember, PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import type { MyInventoryItem } from '@/api/inventory'
import type { ExchangeRequestEntry } from '@/api/types'

defineProps<{
  member: StationMember
  memberId: number
  currentMemberId: number
  tabs: { key: string; label: string }[]
  applicableFields: ProfileField[]
  changes: ProfileFieldChange[]
  showChangeHistory: boolean
  getFieldValue: (fieldId: number) => unknown
  memberUserType: string
  memberPermissions: PermissionGrant[]
  memberGroupList: MemberGroup[]
  memberTagList: UserTag[]
  showManagerSection: boolean
  managers: StationMember[]
  availableManagers: StationMember[]
  managerValues: Map<number, Map<number, string>>
  managerUserTypesAsRoleMap: Map<number, string[]>
  fields: ProfileField[]
  canEdit: boolean
  memberDisplayName: (m: StationMember) => string
  getManagerFields: (id: number) => ProfileField[]
  getManagerFieldValue: (mgrId: number, fieldId: number) => unknown
  memberInventory: MyInventoryItem[]
  memberExchanges: ExchangeRequestEntry[]
  showInventoryManagement: boolean
  canManageInventory: boolean
}>()

defineEmits<{
  (e: 'reload-changes'): void
  (e: 'link-manager', id: number): void
  (e: 'remove-manager', id: number): void
  (e: 'create-manager', data: { firstName: string; lastName: string; email: string }): void
  (e: 'assign-item'): void
  (e: 'request-exchange', item: MyInventoryItem): void
  (e: 'unassign', item: MyInventoryItem): void
  (e: 'reassign', item: MyInventoryItem): void
}>()

const activeTab = ref('profile')
</script>

<template>
  <SectionHeader>{{ memberDisplayName(member) }}</SectionHeader>
  <p v-if="member.email" class="text-sm text-(--text-muted)">{{ member.email }}</p>

  <TabBar v-model="activeTab" :tabs="tabs" />

  <ProfileTab
    v-if="activeTab === 'profile'"
    :applicable-fields="applicableFields" :member-id="memberId"
    :current-member-id="currentMemberId" :changes="changes"
    :show-change-history="showChangeHistory" :get-field-value="getFieldValue"
    @reload="$emit('reload-changes')"
  />

  <PermissionsTab
    v-if="activeTab === 'permissions'"
    :member-user-type="memberUserType" :member-permissions="memberPermissions"
    :member-group-list="memberGroupList" :member-tag-list="memberTagList"
  />

  <GuardiansTab
    v-if="activeTab === 'guardians'"
    :show-manager-section="showManagerSection" :managers="managers"
    :available-managers="availableManagers" :manager-values="managerValues"
    :manager-user-types-as-role-map="managerUserTypesAsRoleMap" :fields="fields"
    :can-edit="canEdit" :member-display-name="memberDisplayName"
    :get-manager-fields="getManagerFields" :get-manager-field-value="getManagerFieldValue"
    @link-manager="$emit('link-manager', $event)"
    @remove-manager="$emit('remove-manager', $event)"
    @create-manager="$emit('create-manager', $event)"
  />

  <AbsencesTab v-if="activeTab === 'absences'" :member-id="memberId" />

  <InventoryTab
    v-if="activeTab === 'inventory'"
    :member-inventory="memberInventory" :member-exchanges="memberExchanges"
    :show-inventory-management="showInventoryManagement"
    :can-manage-inventory="canManageInventory" :can-edit="canEdit"
    @assign-item="$emit('assign-item')"
    @request-exchange="$emit('request-exchange', $event)"
    @unassign="$emit('unassign', $event)"
    @reassign="$emit('reassign', $event)"
  />

  <NeutralContainer v-if="activeTab === 'notes'">
    <NoteEditor :entity-type="'MEMBER'" :entity-id="memberId"/>
  </NeutralContainer>
</template>
