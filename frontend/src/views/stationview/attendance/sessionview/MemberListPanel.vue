/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberEntry from './MemberEntry.vue'
import MemberPickerFilter from '@/views/stationview/members/MemberPickerFilter.vue'
import type {AttendanceEntry, AttendanceStatus} from '@/api/attendance'
import type {MemberGroup, StationMember} from '@/api/types'

const {t} = useI18n()

const selectedMemberId = defineModel<string>('selectedMemberId', {required: true})

const props = defineProps<{
  entries: AttendanceEntry[]
  allMembers: StationMember[]
  memberSections: { group: MemberGroup | null; members: StationMember[] }[]
  readonly?: boolean
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

/** What the reader typed to find the person they want to add to the sheet. */
const search = ref('')

/** The kind of member they are looking for, or the empty string for every kind. */
const userType = ref('')

/** The kinds present among those not on the sheet, so choosing one never empties the list by itself. */
const offeredUserTypes = computed(() => {
  const kinds = new Set<string>()
  for (const member of membersNotInSession.value) {
    if (member.userType) kinds.add(member.userType)
  }
  return [...kinds].sort()
})

/**
 * Those not on the sheet yet, narrowed to what the reader is looking for.
 *
 * <p>Somebody who turns up unannounced is added here, and a station of three hundred offered three
 * hundred names in one dropdown to find them in.
 */
const addableMembers = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return membersNotInSession.value
      .filter(member => !userType.value || member.userType === userType.value)
      .filter(member => !needle || (member.name ?? member.email ?? '').toLowerCase().includes(needle))
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
          @set-status="(entryId, status) => emit('setStatus', entryId, status)"
          @check-in="(entryId, time) => emit('checkIn', entryId, time)"
          @check-out="(entryId, time) => emit('checkOut', entryId, time)"
          @reset-times="(entryId) => emit('resetTimes', entryId)"
      />
    </div>
  </div>

  <!-- Add member -->
  <div v-if="!readonly && membersNotInSession.length > 0" class="space-y-2">
    <MemberPickerFilter v-model:search="search" v-model:user-type="userType" :user-types="offeredUserTypes"/>
    <div class="flex items-center gap-2">
      <SelectInput v-model="selectedMemberId" class="flex-1">
        <option disabled value="">{{ t('attendanceSession.addMember') }}</option>
        <option v-for="m in addableMembers" :key="m.id" :value="String(m.id)">
          {{ m.name ?? m.email }}
        </option>
      </SelectInput>
      <PrimaryButton :icon="['fas', 'plus']" :disabled="!selectedMemberId" @click="emit('addMember')">
        {{ t('attendanceSession.add') }}
      </PrimaryButton>
    </div>
  </div>
</template>
