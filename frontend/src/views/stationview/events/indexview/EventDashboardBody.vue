/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TodayEvents from './TodayEvents.vue'
import EventsByCategory from './EventsByCategory.vue'
import BreaksList from './BreaksList.vue'
import type {AttendanceTemplate, EventBreak, EventCategory, EventField, StationEvent} from '@/api/types'

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

const {t} = useI18n()

defineProps<{
  todayEvents: StationEvent[]
  eventsByCategory: CategoryGroup[]
  hasEvents: boolean
  templates: AttendanceTemplate[]
  overviewFields: Record<number, EventField[]>
  breaks: EventBreak[]
}>()

defineEmits<{
  (e: 'attendance', ev: StationEvent): void
  (e: 'add-event'): void
  (e: 'edit-event', ev: StationEvent): void
  (e: 'delete-event', ev: StationEvent): void
  (e: 'add-break'): void
  (e: 'edit-break', br: EventBreak): void
  (e: 'delete-break', br: EventBreak): void
  (e: 'import-holidays'): void
  (e: 'open-export'): void
}>()
</script>

<template>
  <div class="space-y-6">
    <TodayEvents :events="todayEvents" @attendance="ev => $emit('attendance', ev)"/>

    <EventsByCategory
        :groups="eventsByCategory"
        :has-events="hasEvents"
        :templates="templates"
        :overview-fields="overviewFields"
        @add-event="$emit('add-event')"
        @edit-event="ev => $emit('edit-event', ev)"
        @delete-event="ev => $emit('delete-event', ev)"
    />

    <BreaksList
        :breaks="breaks"
        @add="$emit('add-break')"
        @delete="br => $emit('delete-break', br)"
        @edit="br => $emit('edit-break', br)"
        @import-holidays="$emit('import-holidays')"
    />

    <NeutralContainer class="flex items-center justify-between">
      <SectionHeader>{{ t('events.export') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'file-export']" @click="$emit('open-export')">
        {{ t('events.exportPdf') }}
      </PrimaryButton>
    </NeutralContainer>
  </div>
</template>
