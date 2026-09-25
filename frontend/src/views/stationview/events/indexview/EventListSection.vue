/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import EventRow from './EventRow.vue'
import type {PagedListView} from '@/composables/usePagedList'
import type {AttendanceTemplate} from '@/api/attendance'
import type {DatedEvent, EventCategory, EventField, StationEvent} from '@/api/events'

/**
 * One of the dashboard's lists, with the button that asks the server for the next page of it.
 *
 * <p>The section is absent while it holds nothing rather than showing a heading over emptiness. The
 * page says once that there is nothing here, and says it for both lists together.
 */
const props = defineProps<{
  title: string
  list: PagedListView<DatedEvent>
  isPast: boolean
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  overviewFields: Record<number, EventField[]>
}>()

const emit = defineEmits<{
  edit: [event: StationEvent]
  remove: [event: StationEvent]
  loadMore: []
}>()

const {t} = useI18n()

function categoryOf(item: DatedEvent): EventCategory | null {
  return props.categories.find(category => category.id === item.event.categoryId) ?? null
}

function templateNameOf(item: DatedEvent): string {
  const templateId = item.event.templateId
  if (!templateId) return ''
  return props.templates.find(template => template.id === templateId)?.name ?? ''
}
</script>

<template>
  <div v-if="list.items.length" class="space-y-2" data-testid="event-list-section">
    <SubHeader class="text-sm font-semibold uppercase text-(--text-muted) pt-2">{{ title }}</SubHeader>
    <EventRow
        v-for="item in list.items"
        :key="item.event.id"
        :item="item"
        :is-past="isPast"
        :category="categoryOf(item)"
        :template-name="templateNameOf(item)"
        :fields="overviewFields[item.event.id] ?? []"
        @edit="emit('edit', item.event)"
        @remove="emit('remove', item.event)"
    />
    <SecondaryButton v-if="list.hasMore" class="w-full" :disabled="list.loadingMore" @click="emit('loadMore')">
      <Spinner v-if="list.loadingMore" size="sm" class="mr-2"/>
      {{ t('events.loadMore') }}
    </SecondaryButton>
  </div>
</template>
