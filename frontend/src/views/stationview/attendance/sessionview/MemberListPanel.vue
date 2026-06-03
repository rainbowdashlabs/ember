/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberEntry from './MemberEntry.vue'
import type {AttendanceEntry, AttendanceStatus, MemberGroup, StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  entries: AttendanceEntry[]
  allMembers: StationMember[]
  memberSections: { group: MemberGroup | null; members: StationMember[] }[]
  selectedMemberId: string
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
  addMember: []
  'update:selectedMemberId': [value: string]
}>()

const membersNotInSession = computed(() => {
  const entryMemberIds = new Set(props.entries.map(e => e.memberId))
  return props.allMembers.filter(m => !entryMemberIds.has(m.id) && !m.formerAt)
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
          @set-status="(entryId, status) => emit('setStatus', entryId, status)"
          @check-in="(entryId, time) => emit('checkIn', entryId, time)"
          @check-out="(entryId, time) => emit('checkOut', entryId, time)"
          @reset-times="(entryId) => emit('resetTimes', entryId)"
      />
    </div>
  </div>

  <!-- Add member -->
  <div v-if="membersNotInSession.length > 0" class="flex items-center gap-2">
    <SelectInput :model-value="selectedMemberId" class="flex-1" @update:model-value="emit('update:selectedMemberId', $event ?? '')">
      <option disabled value="">{{ t('attendanceSession.addMember') }}</option>
      <option v-for="m in membersNotInSession" :key="m.id" :value="String(m.id)">
        {{ m.name ?? m.email }}
      </option>
    </SelectInput>
    <PrimaryButton :icon="['fas', 'plus']" :disabled="!selectedMemberId" @click="emit('addMember')">
      {{ t('attendanceSession.add') }}
    </PrimaryButton>
  </div>
</template>
