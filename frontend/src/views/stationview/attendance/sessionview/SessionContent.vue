/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script lang="ts" setup>
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {
  AttendanceEntry,
  AttendanceSession,
  AttendanceSessionField,
  AttendanceStatus,
  AttendanceTemplateField,
  MemberGroup,
  StationMember,
} from '@/api/types'
import SessionToolbar from './SessionToolbar.vue'
import SessionHeader from './SessionHeader.vue'
import CheckModePanel from './CheckModePanel.vue'
import SessionFieldsPanel from './SessionFieldsPanel.vue'
import AttendanceSummary from './AttendanceSummary.vue'
import MemberListPanel from './MemberListPanel.vue'

interface MemberSection {
  group: MemberGroup | null
  members: StationMember[]
}

const selectedMemberId = defineModel<string>('selectedMemberId', {required: true})

defineProps<{
  loading: boolean
  error: string
  session: AttendanceSession | null
  canEdit: boolean
  checkMode: boolean
  checkIndex: number
  uncheckedEntries: AttendanceEntry[]
  currentCheckEntry: AttendanceEntry | null
  currentMemberName: string
  currentMemberIdentity: string | null
  templateFields: AttendanceTemplateField[]
  fieldValues: Map<number, AttendanceSessionField>
  groupMembers: Map<number, StationMember[]>
  allMembers: StationMember[]
  entries: AttendanceEntry[]
  memberSections: MemberSection[]
}>()

const emit = defineEmits<{
  back: []
  export: []
  sync: []
  startCheckMode: []
  updateTitle: [value: string]
  updateStartTime: [value: string]
  updateEndTime: [value: string]
  checkSetStatus: [status: AttendanceStatus]
  skipCheck: []
  endCheckMode: []
  fieldUpdate: [fieldId: number, value: string]
  fieldMemberIds: [fieldId: number, memberIds: number[]]
  setStatus: [entryId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
  addMember: []
}>()
</script>

<template>
  <div class="space-y-6">
    <SessionToolbar
        :check-mode="checkMode"
        :unchecked-count="uncheckedEntries.length"
        :readonly="!canEdit"
        @back="emit('back')"
        @export="emit('export')"
        @sync="emit('sync')"
        @start-check-mode="emit('startCheckMode')"
    />

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && session">
      <SessionHeader
          :session="session"
          :readonly="!canEdit"
          @update-title="emit('updateTitle', $event)"
          @update-start-time="emit('updateStartTime', $event)"
          @update-end-time="emit('updateEndTime', $event)"
      />

      <CheckModePanel
          v-if="checkMode"
          :current-entry="currentCheckEntry"
          :check-index="checkIndex"
          :total-unchecked="uncheckedEntries.length"
          :member-name="currentMemberName"
          :member-identity="currentMemberIdentity"
          @set-status="emit('checkSetStatus', $event)"
          @skip="emit('skipCheck')"
          @end="emit('endCheckMode')"
      />

      <template v-if="!checkMode">
        <SessionFieldsPanel
            :template-fields="templateFields"
            :field-values="fieldValues"
            :group-members="groupMembers"
            :all-members="allMembers"
            :readonly="!canEdit"
            @field-update="(fieldId, value) => emit('fieldUpdate', fieldId, value)"
            @field-member-ids="(fieldId, memberIds) => emit('fieldMemberIds', fieldId, memberIds)"
        />

        <AttendanceSummary :entries="entries" :member-sections="memberSections"/>

        <MemberListPanel
            v-model:selected-member-id="selectedMemberId"
            :entries="entries"
            :all-members="allMembers"
            :member-sections="memberSections"
            :readonly="!canEdit"
            @set-status="(entryId, status) => emit('setStatus', entryId, status)"
            @check-in="(entryId, time) => emit('checkIn', entryId, time)"
            @check-out="(entryId, time) => emit('checkOut', entryId, time)"
            @reset-times="emit('resetTimes', $event)"
            @add-member="emit('addMember')"
        />
      </template>
    </template>
  </div>
</template>
