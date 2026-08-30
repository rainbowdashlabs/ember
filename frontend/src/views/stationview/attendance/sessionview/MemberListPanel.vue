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
import MemberPicker from '@/views/stationview/members/MemberPicker.vue'
import type {AttendanceEntry, AttendanceStatus} from '@/api/attendance'
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
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
  addMember: []
}>()

const membersNotInSession = computed(() => {
  const entryMemberIds = new Set(props.entries.map(e => e.memberId))
  return props.allMembers.filter(m => !entryMemberIds.has(m.id) && !m.formerAt)
})

/** The kinds present among those not on the sheet, so choosing one never empties the list by itself. */
const offeredUserTypes = computed(() => {
  const kinds = new Set<string>()
  for (const member of membersNotInSession.value) {
    if (member.userType) kinds.add(member.userType)
  }
  return [...kinds].sort()
})

/** Those not on the sheet yet, as the picker wants them: a name to read and a face to recognise. */
const addableMembers = computed(() => membersNotInSession.value.map(member => ({
  id: member.id,
  name: member.name ?? member.email ?? `#${member.id}`,
  email: member.email,
  identity: member.identity,
  userType: member.userType,
})))

/** Picking somebody puts them on the sheet, which is the only thing this picker is for. */
function addByHand(memberId: number) {
  selectedMemberId.value = String(memberId)
  emit('addMember')
}

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
          @set-status="(entryId, status) => emit('setStatus', entryId, status)"
          @check-in="(entryId, time) => emit('checkIn', entryId, time)"
          @check-out="(entryId, time) => emit('checkOut', entryId, time)"
          @reset-times="(entryId) => emit('resetTimes', entryId)"
      />
    </div>
  </div>

  <!-- Add member -->
  <MemberPicker
      v-if="!readonly && membersNotInSession.length > 0"
      :members="addableMembers"
      :user-types="offeredUserTypes"
      :placeholder="t('attendanceSession.addMember')"
      @select="addByHand"
  />
</template>
