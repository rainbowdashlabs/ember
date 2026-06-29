/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberRowDesktop from './MemberRowDesktop.vue'
import MemberExpansion from './MemberExpansion.vue'
import type {ProfileField, StationMember} from '@/api/types'

defineProps<{
  members: StationMember[]
  visibleColumns: ProfileField[]
  expandedId: number | null
  getMemberGroups: (memberId: number) => string[]
  getMemberTags: (memberId: number) => string[]
  isFieldApplicable: (memberId: number, field: ProfileField) => boolean
  getFieldValue: (memberId: number, fieldId: number) => unknown
  getApplicableOverviewFields: (memberId: number) => ProfileField[]
  getManagers: (memberId: number) => StationMember[]
  managerName: (mgr: StationMember) => string
  exportMode?: boolean
  selectedIds?: Set<number>
  canEdit?: boolean
}>()

const emit = defineEmits<{
  rowClick: [member: StationMember]
  toggleSelect: [memberId: number]
  navigateDetail: [member: StationMember, event: Event]
  navigateEdit: [member: StationMember, event: Event]
  resendSetup: [member: StationMember, event: Event]
}>()
</script>

<template>
  <tbody>
    <template v-for="member in members" :key="member.id">
      <MemberRowDesktop
          :member="member"
          :visible-columns="visibleColumns"
          :member-groups="getMemberGroups(member.id)"
          :member-tags="getMemberTags(member.id)"
          :is-field-applicable="(f) => isFieldApplicable(member.id, f)"
          :get-field-value="(id) => getFieldValue(member.id, id)"
          :expanded="expandedId === member.id"
          :export-mode="exportMode"
          :selected="selectedIds?.has(member.id)"
          :can-edit="canEdit"
          @click="emit('rowClick', member)"
          @toggle-select="emit('toggleSelect', member.id)"
          @navigate-detail="(e) => emit('navigateDetail', member, e)"
          @navigate-edit="(e) => emit('navigateEdit', member, e)"
          @resend-setup="(e) => emit('resendSetup', member, e)"
      />
      <MemberExpansion
          v-if="!exportMode && expandedId === member.id"
          :member="member"
          :col-span="visibleColumns.length + 8"
          :overview-fields="getApplicableOverviewFields(member.id)"
          :managers="getManagers(member.id)"
          :get-field-value-for="getFieldValue"
          :get-overview-fields-for="getApplicableOverviewFields"
          :manager-name="managerName"
      />
    </template>
  </tbody>
</template>
