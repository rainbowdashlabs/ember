/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MemberEntry from './MemberEntry.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember, userTypesOf} from '@/components/input/select/memberOption'
import {useMemberPick} from '@/composables/useMemberPick'
import type {AttendanceEntry, AttendanceStatus, MemberNotes} from '@/api/attendance'
import type {MemberGroup, StationMember} from '@/api/types'

const {t} = useI18n()

const selectedMemberId = defineModel<string>('selectedMemberId', {required: true})

const props = defineProps<{
  entries: AttendanceEntry[]
  allMembers: StationMember[]
  memberSections: { group: MemberGroup | null; members: StationMember[] }[]
  readonly?: boolean
  sessionStart?: string
  sessionEnd?: string
  spansDays?: boolean
  /** Absent where the screen has no notes to show, as the pitch does not. */
  memberNotes?: Map<number, MemberNotes>
  canManageSwap?: boolean
  canSignOffFound?: boolean
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  enter: [memberId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
  addMember: []
  moveSwap: [movementId: number, stepId: number, replacementItemId: number | null]
  dropSwap: [movementId: number]
  signOffFound: [itemId: number]
}>()

const membersNotInSession = computed(() => {
  const entryMemberIds = new Set(props.entries.map(e => e.memberId))
  return props.allMembers.filter(m => !entryMemberIds.has(m.id) && !m.formerAt)
})

/** Those not on the sheet yet, as the menu wants them: a name to read and a face to recognise. */
const addableMembers = computed(() => membersNotInSession.value.map(fromMember))

/** The kinds present among them, so choosing one never empties the list by itself. */
const offeredUserTypes = computed(() => userTypesOf(addableMembers.value).toSorted())

/** Picking somebody puts them on the sheet, which is the only thing this menu is for. */
const {picked, take} = useMemberPick(memberId => {
  selectedMemberId.value = String(memberId)
  emit('addMember')
})

function getMemberName(memberId: number): string {
  const m = props.allMembers.find(mm => mm.id === memberId)
  return m?.name ?? m?.email ?? `#${memberId}`
}

function getEntry(memberId: number): AttendanceEntry | undefined {
  return props.entries.find(e => e.memberId === memberId)
}
</script>

<template>
  <!-- Members by group -->
  <div v-for="section in memberSections" :key="section.group?.id ?? 'ungrouped'" class="space-y-2">
    <SubHeader>{{ section.group?.name ?? t('attendanceSession.otherMembers') }}</SubHeader>
    <div class="space-y-1">
      <MemberEntry
          v-for="member in section.members"
          :key="member.id"
          :member="member"
          :entry="getEntry(member.id)"
          :member-name="getMemberName(member.id)"
          :readonly="readonly"
          :session-end="sessionEnd"
          :session-start="sessionStart"
          :spans-days="spansDays"
          :notes="memberNotes?.get(member.id)"
          :can-manage-swap="canManageSwap"
          :can-sign-off-found="canSignOffFound"
          @set-status="(entryId, status) => emit('setStatus', entryId, status)"
          @enter="(memberId, status) => emit('enter', memberId, status)"
          @move-swap="(movementId, stepId, replacementItemId) => emit('moveSwap', movementId, stepId, replacementItemId)"
          @drop-swap="(movementId) => emit('dropSwap', movementId)"
          @sign-off-found="(itemId) => emit('signOffFound', itemId)"
          @check-in="(entryId, time) => emit('checkIn', entryId, time)"
          @check-out="(entryId, time) => emit('checkOut', entryId, time)"
          @reset-times="(entryId) => emit('resetTimes', entryId)"
      />
    </div>
  </div>

  <!-- Add member -->
  <MemberSelectInput
      v-if="!readonly && membersNotInSession.length > 0"
      v-model="picked"
      :members="addableMembers"
      :user-types="offeredUserTypes"
      :placeholder="t('attendanceSession.addMember')"
      @change="take"
  />
</template>
