/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {useBreakpoint} from '@/composables/useBreakpoint'
import type {AttendanceEntry, AttendanceStatus, StationMember} from '@/api/types'

const {t} = useI18n()
const {isMobile} = useBreakpoint()

const props = defineProps<{
  member: StationMember
  entry?: AttendanceEntry
  memberName: string
}>()

const emit = defineEmits<{
  setStatus: [entryId: number, status: AttendanceStatus]
  checkIn: [entryId: number, time: string]
  checkOut: [entryId: number, time: string]
  resetTimes: [entryId: number]
}>()

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<template>
  <div
      :class="{
        'border-success bg-success/5': entry?.status === 'PRESENT',
        'border-error bg-error/5': entry?.status === 'ABSENT',
        'border-info bg-info/5': entry?.status === 'DECLINED',
        'border-bg-light-accent dark:border-bg-dark-accent bg-bg-light-accent/20 dark:bg-bg-dark-accent/20': !entry || entry?.status === 'UNCONFIRMED',
      }"
      class="rounded-lg px-4 py-3 border-l-4 transition-all"
  >
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="flex items-center gap-2 min-w-0">
        <font-awesome-icon
            v-if="entry?.status === 'PRESENT'"
            :icon="['fas', 'check']" class="h-4 w-4 text-success shrink-0"
        />
        <font-awesome-icon
            v-else-if="entry?.status === 'ABSENT'"
            :icon="['fas', 'xmark']" class="h-4 w-4 text-error shrink-0"
        />
        <font-awesome-icon
            v-else-if="entry?.status === 'DECLINED'"
            :icon="['fas', 'ban']" class="h-4 w-4 text-info shrink-0"
        />
        <font-awesome-icon
            v-else
            :icon="['fas', 'asterisk']" class="h-4 w-4 text-(--text-muted) shrink-0"
        />
        <MemberName :identity="member.identity" class="font-medium text-sm truncate"/>
      </div>
      <div v-if="entry" class="flex flex-col sm:flex-row sm:items-center gap-2">
        <!-- Status buttons -->
        <div class="flex items-center gap-2 w-full sm:w-auto">
          <SuccessButton
              :class="{ 'opacity-40': entry.status === 'PRESENT' }"
              :disabled="entry.status === 'PRESENT'"
              :full-width="isMobile"
              class="text-xs flex-1 sm:flex-initial"
              @click="emit('setStatus', entry.id, 'PRESENT')"
          >
            <font-awesome-icon :icon="['fas', 'check']"/>
          </SuccessButton>
          <ErrorButton
              :class="{ 'opacity-40': entry.status === 'ABSENT' }"
              :disabled="entry.status === 'ABSENT'"
              :full-width="isMobile"
              class="text-xs flex-1 sm:flex-initial"
              @click="emit('setStatus', entry.id, 'ABSENT')"
          >
            <font-awesome-icon :icon="['fas', 'xmark']"/>
          </ErrorButton>
          <InfoButton
              :class="{ 'opacity-40': entry.status === 'DECLINED' }"
              :disabled="entry.status === 'DECLINED'"
              :full-width="isMobile"
              class="text-xs flex-1 sm:flex-initial"
              @click="emit('setStatus', entry.id, 'DECLINED')"
          >
            <font-awesome-icon :icon="['fas', 'ban']"/>
          </InfoButton>
        </div>
        <!-- Time inputs for PRESENT -->
        <div v-if="entry.status === 'PRESENT'" class="flex items-center gap-1 text-xs">
          <TimeShortInput
              :model-value="formatTime(entry.checkIn)"
              class="w-20 text-xs"
              @change="emit('checkIn', entry.id, ($event.target as HTMLInputElement).value)"
          />
          <span class="text-(--text-muted)">–</span>
          <TimeShortInput
              :model-value="formatTime(entry.checkOut)"
              class="w-20 text-xs"
              @change="emit('checkOut', entry.id, ($event.target as HTMLInputElement).value)"
          />
          <IconButton
              v-if="entry.checkIn || entry.checkOut"
              :icon="['fas', 'xmark']"
              :label="t('attendanceSession.resetTimes')"
              @click="emit('resetTimes', entry.id)"
          />
        </div>
      </div>
      <span v-else class="text-xs text-(--text-muted)">{{ t('attendanceSession.noEntry') }}</span>
    </div>
  </div>
</template>
