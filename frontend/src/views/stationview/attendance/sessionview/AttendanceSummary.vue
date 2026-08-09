/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {AttendanceEntry} from '@/api/attendance'
import type {MemberGroup, StationMember} from '@/api/types'

const {t} = useI18n()

const props = defineProps<{
  entries: AttendanceEntry[]
  memberSections: { group: MemberGroup | null; members: StationMember[] }[]
}>()

function countByStatus(entries: AttendanceEntry[]) {
  let unconfirmed = 0, present = 0, absent = 0, declined = 0
  for (const e of entries) {
    if (e.status === 'UNCONFIRMED') unconfirmed++
    else if (e.status === 'PRESENT') present++
    else if (e.status === 'ABSENT') absent++
    else if (e.status === 'DECLINED') declined++
  }
  return {unconfirmed, present, absent, declined}
}

const totalCounts = computed(() => countByStatus(props.entries))

const sectionCounts = computed(() => {
  const entryByMember = new Map(props.entries.map(e => [e.memberId, e]))
  return props.memberSections.map(section => {
    const sectionEntries = section.members
        .map(m => entryByMember.get(m.id))
        .filter((e): e is AttendanceEntry => e != null)
    return {
      group: section.group,
      counts: countByStatus(sectionEntries),
    }
  })
})
</script>

<template>
  <div class="space-y-2">
    <!-- Per-group summaries -->
    <div v-for="sc in sectionCounts" :key="sc.group?.id ?? 'ungrouped'" class="flex items-center gap-3 text-sm flex-wrap">
      <span class="font-medium min-w-24">{{ sc.group?.name ?? t('attendanceSession.otherMembers') }}</span>
      <SecondaryBadge v-if="sc.counts.unconfirmed > 0">
        {{ sc.counts.unconfirmed }} {{ t('attendanceSession.unconfirmed') }}
      </SecondaryBadge>
      <SuccessBadge>{{ sc.counts.present }} {{ t('attendanceSession.present') }}</SuccessBadge>
      <ErrorBadge>{{ sc.counts.absent }} {{ t('attendanceSession.absent') }}</ErrorBadge>
      <InfoBadge>{{ sc.counts.declined }} {{ t('attendanceSession.declined') }}</InfoBadge>
    </div>

    <!-- Total summary -->
    <div v-if="sectionCounts.length > 1" class="flex items-center gap-3 text-sm flex-wrap border-t border-(--border) pt-2">
      <span class="font-medium min-w-24">{{ t('attendanceSession.total') }}</span>
      <SecondaryBadge v-if="totalCounts.unconfirmed > 0">
        {{ totalCounts.unconfirmed }} {{ t('attendanceSession.unconfirmed') }}
      </SecondaryBadge>
      <SuccessBadge>{{ totalCounts.present }} {{ t('attendanceSession.present') }}</SuccessBadge>
      <ErrorBadge>{{ totalCounts.absent }} {{ t('attendanceSession.absent') }}</ErrorBadge>
      <InfoBadge>{{ totalCounts.declined }} {{ t('attendanceSession.declined') }}</InfoBadge>
    </div>
  </div>
</template>
