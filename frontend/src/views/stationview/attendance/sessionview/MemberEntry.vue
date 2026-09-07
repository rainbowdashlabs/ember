/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MemberName from '@/components/avatar/MemberName.vue'
import MemberEntryStatusIcon from './memberentry/MemberEntryStatusIcon.vue'
import MemberEntryActions from './memberentry/MemberEntryActions.vue'
import MemberEntryStatusButtons from './memberentry/MemberEntryStatusButtons.vue'
import MemberEntryReadonlyTimes from './memberentry/MemberEntryReadonlyTimes.vue'
import MemberCheckNotes from './MemberCheckNotes.vue'
import type {AttendanceEntry, AttendanceStatus, MemberNotes} from '@/api/attendance'
import type {StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  member: StationMember
  entry?: AttendanceEntry
  memberName: string
  readonly?: boolean
  sessionStart?: string
  sessionEnd?: string
  notes?: MemberNotes
  canMoveSwap?: boolean
  canSignOffFound?: boolean
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  enter: [memberId: number, status: AttendanceStatus]
  moveSwap: [exchangeId: number, nextStatus: string, replacementItemId: number | null]
  signOffFound: [itemId: number]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
}>()

/**
 * Whether the member had joined the station by the evening this sheet is about. A member entered
 * afterwards was not there, so nothing is offered to record about them; a member with no join date
 * carries no restriction, which is the state every member had before the field was filled in.
 */
const hadJoined = computed(() => {
  if (!props.member.joinDate || !props.sessionStart) return true
  return props.member.joinDate <= props.sessionStart.slice(0, 10)
})
</script>

<template>
  <div
      :class="{
        'border-success bg-success/5': entry?.status === 'PRESENT',
        'border-error bg-error/5': entry?.status === 'ABSENT',
        'border-info bg-info/5': entry?.status === 'DECLINED',
        'border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20': !entry || entry?.status === 'UNCONFIRMED',
      }"
      :data-testid="`member-row-${member.id}`"
      class="rounded-lg px-4 py-3 border-l-4 transition-all"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <MemberEntryStatusIcon :status="entry?.status"/>
        <MemberName :identity="member.identity" class="font-medium text-sm truncate"/>
      </div>
      <MemberEntryActions
          v-if="entry && !readonly"
          :entry="entry"
          :session-end="sessionEnd"
          :session-start="sessionStart"
          @set-status="emit('setStatus', entry.id, $event)"
          @check-in="emit('checkIn', entry.id, $event)"
          @check-out="emit('checkOut', entry.id, $event)"
          @reset-times="emit('resetTimes', entry.id)"
      />
      <MemberEntryReadonlyTimes
          v-else-if="entry && readonly"
          :entry="entry"
          :session-end="sessionEnd"
          :session-start="sessionStart"
      />
      <MemberEntryStatusButtons
          v-else-if="!readonly && hadJoined"
          @set-status="emit('enter', member.id, $event)"
      />
      <span v-else class="text-xs text-(--text-muted)">
        {{ hadJoined ? t('attendanceSession.noEntry') : t('attendanceSession.beforeJoining') }}
      </span>
    </div>
    <MemberCheckNotes
        :notes="notes"
        :can-move-swap="canMoveSwap"
        :can-sign-off-found="canSignOffFound"
        class="mt-2"
        @move-swap="(exchangeId, nextStatus, replacementItemId) => emit('moveSwap', exchangeId, nextStatus, replacementItemId)"
        @sign-off-found="(itemId) => emit('signOffFound', itemId)"
    />
  </div>
</template>
