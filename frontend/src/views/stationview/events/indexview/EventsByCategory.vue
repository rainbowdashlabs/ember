/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {reactive} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type {AttendanceTemplate, EventCategory, EventField, StationEvent} from '@/api/types'
import {EventTypes, isRecurringEvent} from '@/api/types'
import MutedIcon from '@/components/display/MutedIcon.vue'
import EventFieldValue from '@/components/display/EventFieldValue.vue'

const {t} = useI18n()
const router = useRouter()

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

defineProps<{
  groups: CategoryGroup[]
  templates: AttendanceTemplate[]
  hasEvents: boolean
  overviewFields?: Record<number, EventField[]>
}>()

const emit = defineEmits<{
  addEvent: []
  editEvent: [event: StationEvent]
  deleteEvent: [event: StationEvent]
}>()

// Track which categories are expanded beyond their maxShownEvents limit
const expandedCategories = reactive(new Set<number | 'none'>())

function visibleEvents(group: CategoryGroup) {
  const max = group.category?.maxShownEvents
  const key = group.category?.id ?? 'none'
  if (!max || expandedCategories.has(key)) return group.events
  return group.events.slice(0, max)
}

function hasMore(group: CategoryGroup): boolean {
  const max = group.category?.maxShownEvents
  const key = group.category?.id ?? 'none'
  if (!max || expandedCategories.has(key)) return false
  return group.events.length > max
}

function toggleExpand(group: CategoryGroup) {
  const key = group.category?.id ?? 'none'
  if (expandedCategories.has(key)) expandedCategories.delete(key)
  else expandedCategories.add(key)
}

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

function eventTypeLabel(eventType?: string): string {
  if (eventType === EventTypes.RECURRING) return t('events.typeRecurring')
  if (eventType === EventTypes.MONTHLY_FIRST) return t('events.typeMonthlyFirst')
  if (eventType === EventTypes.QUARTERLY) return t('events.typeQuarterly')
  if (eventType === EventTypes.YEARLY) return t('events.typeYearly')
  return t('events.typeOneTime')
}

function formatDate(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('events.allEvents') }}</SectionHeader>
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'folder-plus']" @click="router.push({name: 'event-categories'})">
          {{ t('events.manageCategories') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'layer-group']" @click="router.push({name: 'event-layouts'})">
          {{ t('sidebar.eventLayouts') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'calendar-plus']" @click="router.push({name: 'event-batch'})">
          {{ t('sidebar.eventBatch') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']" @click="emit('addEvent')">
          {{ t('events.addEvent') }}
        </PrimaryButton>
      </div>
    </div>

    <EmptyState compact v-if="!hasEvents">{{ t('events.noEvents') }}</EmptyState>

    <div v-for="group in groups" :key="group.category?.id ?? 'none'" class="space-y-2">
      <div v-if="group.category" class="pt-2">
        <SubHeader class="text-sm font-semibold uppercase text-(--text-muted)">{{ group.category.name }}</SubHeader>
      </div>
      <SubHeader v-else class="text-sm font-semibold uppercase text-(--text-muted) pt-2">{{ t('events.uncategorized') }}</SubHeader>

      <NeutralContainer v-for="ev in visibleEvents(group)" :key="ev.id" class="flex items-center justify-between cursor-pointer hover:bg-(--bg-accent) transition-colors"
                        @click="router.push({ name: 'event-detail', params: { id: ev.id } })">
        <div class="flex items-center gap-2 flex-wrap">
          <SecondaryBadge v-if="isRecurringEvent(ev.eventType)">
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>
            {{ eventTypeLabel(ev.eventType) }}
          </SecondaryBadge>
          <span class="font-medium text-primary">{{ ev.name }}</span>
          <MutedIcon v-if="ev.restricted" :icon="['fas', 'lock']" class="ml-1"/>
          <span v-if="isRecurringEvent(ev.eventType)" class="text-sm text-(--text-muted)">{{
              dayName(ev.dayOfWeek)
            }}, {{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
          <span v-else class="text-sm text-(--text-muted)">{{ formatDate(ev.startTime) }}, {{
              formatTime(ev.startTime)
            }} – {{ formatTime(ev.endTime) }}</span>
          <span v-if="ev.templateId" class="text-xs text-primary">{{ templateName(ev.templateId, templates) }}</span>
          <template v-if="overviewFields?.[ev.id]?.length">
            <span v-for="f in overviewFields[ev.id]" :key="f.id" class="text-xs text-(--text-muted)">
              {{ f.name }}: <EventFieldValue :field-type="f.fieldType" :value="f.value"/>
            </span>
          </template>
        </div>
        <div class="flex items-center gap-2">
          <EditButton @click.stop="emit('editEvent', ev)"/>
          <DeleteButton @click.stop="emit('deleteEvent', ev)"/>
        </div>
      </NeutralContainer>

      <SecondaryButton v-if="hasMore(group)" class="w-full" @click="toggleExpand(group)">
        {{ t('events.loadMore', {count: group.events.length - (group.category?.maxShownEvents ?? 0)}) }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
