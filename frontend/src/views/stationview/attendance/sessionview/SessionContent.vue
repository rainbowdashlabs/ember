/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {
  AttendanceEntry,
  AttendanceSession,
  AttendanceStatus,
  AttendanceTemplateField,
  MemberNotes,
} from '@/api/attendance'
import type {MemberGroup, MemberIdentity, StationMember} from '@/api/types'
import type {CheckRow} from './useCheckMode'
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

const {t} = useI18n()

const selectedMemberId = defineModel<string>('selectedMemberId', {required: true})

defineProps<{
  loading: boolean
  error: string
  session: AttendanceSession | null
  canEdit: boolean
  locked: boolean
  canManage: boolean
  memberNotes: Map<number, MemberNotes>
  canMoveSwap: boolean
  canSignOffFound: boolean
  checkMode: boolean
  checkIndex: number
  openRows: CheckRow[]
  currentCheckRow: CheckRow | null
  currentMemberName: string
  currentMemberIdentity: MemberIdentity | null
  templateFields: AttendanceTemplateField[]
  fieldValues: Map<number, string>
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
  remove: []
  updateTitle: [value: string]
  updateStartTime: [value: string]
  updateEndTime: [value: string]
  checkSetStatus: [status: AttendanceStatus]
  skipCheck: []
  endCheckMode: []
  fieldUpdate: [fieldId: number, value: string, immediate: boolean]
  fieldMemberIds: [fieldId: number, memberIds: string[]]
  setStatus: [entryId: number, status: AttendanceStatus]
  enter: [memberId: number, status: AttendanceStatus]
  unlock: []
  lock: []
  moveSwap: [exchangeId: number, nextStatus: string, replacementItemId: number | null]
  signOffFound: [itemId: number]
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
        :unchecked-count="openRows.length"
        :readonly="!canEdit"
        :locked="locked"
        :can-manage="canManage"
        @unlock="emit('unlock')"
        @lock="emit('lock')"
        @back="emit('back')"
        @export="emit('export')"
        @sync="emit('sync')"
        @start-check-mode="emit('startCheckMode')"
        @remove="emit('remove')"
    />

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="locked" variant="info">
      {{ canManage ? t('attendanceSession.frozenForManager') : t('attendanceSession.frozen') }}
    </Alert>
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
          :current-row="currentCheckRow"
          :check-index="checkIndex"
          :total-unchecked="openRows.length"
          :member-name="currentMemberName"
          :member-identity="currentMemberIdentity"
          :notes="currentCheckRow ? memberNotes.get(currentCheckRow.memberId) : undefined"
          :can-move-swap="canMoveSwap"
          :can-sign-off-found="canSignOffFound"
          @set-status="emit('checkSetStatus', $event)"
          @skip="emit('skipCheck')"
          @end="emit('endCheckMode')"
          @move-swap="(exchangeId, nextStatus, replacementItemId) => emit('moveSwap', exchangeId, nextStatus, replacementItemId)"
          @sign-off-found="(itemId) => emit('signOffFound', itemId)"
      />

      <template v-if="!checkMode">
        <SessionFieldsPanel
            :template-fields="templateFields"
            :field-values="fieldValues"
            :group-members="groupMembers"
            :all-members="allMembers"
            :readonly="!canEdit"
            @field-update="(fieldId, value, immediate) => emit('fieldUpdate', fieldId, value, immediate)"
            @field-member-ids="(fieldId, memberIds) => emit('fieldMemberIds', fieldId, memberIds)"
        />

        <AttendanceSummary :entries="entries" :member-sections="memberSections"/>

        <MemberListPanel
            v-model:selected-member-id="selectedMemberId"
            :entries="entries"
            :all-members="allMembers"
            :member-sections="memberSections"
            :readonly="!canEdit"
            :session-end="session.endTime"
            :session-start="session.startTime"
            :member-notes="memberNotes"
            :can-move-swap="canMoveSwap"
            :can-sign-off-found="canSignOffFound"
            @set-status="(entryId, status) => emit('setStatus', entryId, status)"
            @enter="(memberId, status) => emit('enter', memberId, status)"
            @move-swap="(exchangeId, nextStatus, replacementItemId) => emit('moveSwap', exchangeId, nextStatus, replacementItemId)"
            @sign-off-found="(itemId) => emit('signOffFound', itemId)"
            @check-in="(entryId, time) => emit('checkIn', entryId, time)"
            @check-out="(entryId, time) => emit('checkOut', entryId, time)"
            @reset-times="emit('resetTimes', $event)"
            @add-member="emit('addMember')"
        />
      </template>
    </template>
  </div>
</template>
