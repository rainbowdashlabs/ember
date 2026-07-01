/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TodayEventCard from './TodayEventCard.vue'
import type {EventField, StationEvent} from '@/api/types'

defineProps<{
  events: StationEvent[]
  overviewFields: Record<number, EventField[]>
  canManageAttendance: boolean
  detailRoute: (event: StationEvent) => RouteLocationRaw
  formatTime: (iso?: string) => string
}>()

const emit = defineEmits<{
  attendance: [event: StationEvent]
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="events.length > 0" class="space-y-3">
    <SubHeader>{{ t('eventsUpcoming.today') }}</SubHeader>
    <div class="grid gap-3 sm:grid-cols-2">
      <TodayEventCard
          v-for="ev in events"
          :key="ev.id"
          :event="ev"
          :detail-route="detailRoute(ev)"
          :overview-fields="overviewFields[ev.id] ?? []"
          :format-time="formatTime"
          :show-attendance="!!ev.templateId && canManageAttendance"
          @attendance="emit('attendance', $event)"
      />
    </div>
  </div>
  <EmptyState v-else compact>{{ t('eventsUpcoming.noToday') }}</EmptyState>
</template>
