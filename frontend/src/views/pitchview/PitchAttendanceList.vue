/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AttendanceSummary from '@/views/stationview/attendance/sessionview/AttendanceSummary.vue'
import MemberListPanel from '@/views/stationview/attendance/sessionview/MemberListPanel.vue'
import type {PitchAttendance} from './pitchTypes'

/** The attendance of a session, drawn by the application's own summary and member list. */
defineProps<{
  attendance: PitchAttendance
}>()
</script>

<template>
  <AttendanceSummary :entries="attendance.entries" :member-sections="attendance.sections"/>
  <MemberListPanel
      :selected-member-id="''"
      :entries="attendance.entries"
      :all-members="attendance.members"
      :member-sections="attendance.sections"/>
</template>
