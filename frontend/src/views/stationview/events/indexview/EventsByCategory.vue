/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {AttendanceTemplate, EventCategory, StationEvent} from '@/api/types'

const {t} = useI18n()

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

defineProps<{
  groups: CategoryGroup[]
  templates: AttendanceTemplate[]
  hasEvents: boolean
}>()

const emit = defineEmits<{
  addEvent: []
  editEvent: [event: StationEvent]
  deleteEvent: [event: StationEvent]
  addCategory: []
  editCategory: [category: EventCategory]
  deleteCategory: [id: number]
}>()

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

function dayName(day: number | null | undefined): string {
  return day ? dayNames[day] ?? '' : ''
}

function templateName(id: number | null | undefined, templates: AttendanceTemplate[]): string {
  if (!id) return ''
  return templates.find(t => t.id === id)?.name ?? ''
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function formatDate(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('events.allEvents') }}</SectionHeader>
      <div class="flex items-center gap-2">
        <SecondaryButton class="text-sm" @click="emit('addCategory')">
          <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-1"/>
          {{ t('events.addCategory') }}
        </SecondaryButton>
        <PrimaryButton @click="emit('addEvent')">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
          {{ t('events.addEvent') }}
        </PrimaryButton>
      </div>
    </div>

    <div v-if="!hasEvents" class="text-center text-(--text-muted) py-4">
      {{ t('events.noEvents') }}
    </div>

    <div v-for="group in groups" :key="group.category?.id ?? 'none'" class="space-y-2">
      <div v-if="group.category" class="flex items-center justify-between pt-2">
        <h3 class="text-sm font-semibold uppercase text-(--text-muted)">{{ group.category.name }}</h3>
        <div class="flex items-center gap-1">
          <EditButton @click="emit('editCategory', group.category!)"/>
          <DeleteButton @click="emit('deleteCategory', group.category!.id)"/>
        </div>
      </div>
      <h3 v-else class="text-sm font-semibold uppercase text-(--text-muted) pt-2">{{ t('events.uncategorized') }}</h3>

      <NeutralContainer v-for="ev in group.events" :key="ev.id" class="flex items-center justify-between">
        <div class="flex items-center gap-2 flex-wrap">
          <SecondaryBadge v-if="ev.eventType === 'RECURRING'">
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>
            {{ t('events.typeRecurring') }}
          </SecondaryBadge>
          <span class="font-medium">{{ ev.name }}</span>
          <span v-if="ev.eventType === 'RECURRING'" class="text-sm text-(--text-muted)">{{
              dayName(ev.dayOfWeek)
            }}, {{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
          <span v-else class="text-sm text-(--text-muted)">{{ formatDate(ev.startTime) }}, {{
              formatTime(ev.startTime)
            }} – {{ formatTime(ev.endTime) }}</span>
          <span v-if="ev.templateId" class="text-xs text-primary">{{ templateName(ev.templateId, templates) }}</span>
        </div>
        <div class="flex items-center gap-2">
          <EditButton @click="emit('editEvent', ev)"/>
          <DeleteButton @click="emit('deleteEvent', ev)"/>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
