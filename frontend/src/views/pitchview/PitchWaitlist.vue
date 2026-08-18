/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import WaitingListsGrid from '@/views/stationview/members/waitinglist/listview/WaitingListsGrid.vue'
import PendingSection from '@/views/stationview/members/waitinglist/detailview/PendingSection.vue'
import TestingSection from '@/views/stationview/members/waitinglist/detailview/TestingSection.vue'
import OverviewSection from '@/views/stationview/members/waitinglist/detailview/OverviewSection.vue'
import WaitingSectionDesktop
  from '@/views/stationview/members/waitinglist/detailview/waitingsection/WaitingSectionDesktop.vue'
import type {PitchWaitlist} from './pitchTypes'

/** The waiting list of a station, drawn by the application's own sections. */
defineProps<{
  waitlist: PitchWaitlist
  section: 'lists' | 'pending' | 'waiting' | 'testing' | 'settings'
}>()
</script>

<template>
  <WaitingListsGrid v-if="section === 'lists'" :lists="waitlist.lists ?? []"/>

  <OverviewSection v-else-if="section === 'settings'" :list="waitlist.list" :list-id="waitlist.list.id"
                   :fields="waitlist.fields" :groups="waitlist.groups" readonly/>

  <PendingSection v-else-if="section === 'pending'" :entries="waitlist.pending" :fields="waitlist.fields"/>

  <TestingSection v-else-if="section === 'testing'" :entries="waitlist.testing"
                  :attendance-threshold="waitlist.list.attendanceThreshold"/>

  <WaitingSectionDesktop v-else :entries="waitlist.waiting" :visible-fields="waitlist.fields.slice(0, 2)"
                         :expanded-id="null" readonly/>
</template>
