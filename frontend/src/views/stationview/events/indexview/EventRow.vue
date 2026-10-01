/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useEventRoutes} from '@/composables/useEventRoutes'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import EventFieldValue from '../eventshared/EventFieldValue.vue'
import {isRecurringEvent, multiDayEndDate} from '@/api/events'
import type {DatedEvent, EventCategory, EventField} from '@/api/generated/schema'
import {eventTypeLabelKey} from '../eventshared/eventTypeLabel'
import {formatDate, formatDaySpan, formatTime, toIsoDate, todayIsoDate, weekdayName} from '@/util/format'

/**
 * One appointment in the dashboard's lists.
 *
 * <p>The row wears its category rather than standing under a heading that names it, which is what
 * lets the list be ordered by date with every category mixed into it.
 */
const props = defineProps<{
  item: DatedEvent
  /**
   * Whether the row is on the past tab, which decides which of the two dates it is about. Every row
   * carries both, so the tab and not the data says which one the reader came for.
   */
  isPast: boolean
  /** What kind of appointment this is, absent where it was put in no category. */
  category?: EventCategory | null
  /** The attendance template it records against, empty where it records against none. */
  templateName: string
  fields: EventField[]
}>()

const emit = defineEmits<{
  edit: []
  remove: []
}>()

const {t} = useI18n()
const eventRoutes = useEventRoutes()

const recurring = computed(() => isRecurringEvent(props.item.event.eventType))

/**
 * What the row says about when the appointment happens: a series says which day of the week it
 * takes, a one-off says its date, and both say the hours. A one-off running over several days says
 * both of its ends instead, each with its clock.
 */
const when = computed(() => {
  const event = props.item.event
  const startDay = event.startTime ? toIsoDate(new Date(event.startTime)) : ''
  const endDay = startDay ? multiDayEndDate(event, startDay) : null
  if (endDay) return formatDaySpan(startDay, formatTime(event.startTime), endDay, formatTime(event.endTime))
  const hours = `${formatTime(event.startTime)} – ${formatTime(event.endTime)}`
  const lead = recurring.value ? weekdayName(event.dayOfWeek ?? 0) : formatDate(event.startTime)
  return lead ? `${lead}, ${hours}` : hours
})

const typeLabel = computed(() => t(eventTypeLabelKey(props.item.event.eventType)))

/** The date the row is about, which is the one its tab is ordered by, or nothing where it has none. */
const shownDate = computed(() => props.isPast ? props.item.previousDate : props.item.nextDate)

/**
 * What a series says about its own dates. A one-off already writes its date where the hours are, so
 * saying it twice would only add noise.
 */
const dateNote = computed(() => {
  if (!recurring.value || !shownDate.value) return ''
  const date = formatDate(shownDate.value)
  return props.isPast ? t('events.previouslyOn', {date}) : t('events.nextOn', {date})
})

/**
 * The occurrence the row opens. A series opens on the date the row is showing, so the reader lands
 * on the one they were looking at rather than on whichever the detail view would have picked.
 */
const detailRoute = computed(() => {
  if (!recurring.value) return {name: eventRoutes.detail, params: {id: props.item.event.id}}
  return {
    name: eventRoutes.detailOnDate,
    params: {id: props.item.event.id, date: shownDate.value ?? todayIsoDate()},
  }
})
</script>

<template>
  <RowLink :to="detailRoute">
    <NeutralContainer data-testid="event-entry"
                      :data-registration="item.event.requiresRegistration ? 'true' : 'false'"
                      class="flex items-center justify-between cursor-pointer hover:bg-(--bg-accent) transition-colors">
      <div class="flex items-center gap-2 flex-wrap">
        <ColorBadge v-if="category" :color="category.color" data-testid="event-entry-category">
          {{ category.name }}
        </ColorBadge>
        <SecondaryBadge v-if="recurring">
          <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>
          {{ typeLabel }}
        </SecondaryBadge>
        <span class="font-medium text-primary" :class="{'line-through': item.cancellation}">{{ item.event.name }}</span>
        <ErrorBadge v-if="item.cancellation" data-testid="event-entry-cancelled">{{ t('events.cancelled') }}</ErrorBadge>
        <MutedIcon v-if="item.event.restricted" :icon="['fas', 'lock']" class="ml-1"/>
        <span class="text-sm text-(--text-muted)">{{ when }}</span>
        <span v-if="dateNote" class="text-sm text-(--text-muted)">{{ dateNote }}</span>
        <span v-if="templateName" class="text-xs text-primary">{{ templateName }}</span>
        <span v-for="field in fields" :key="field.id" class="text-xs text-(--text-muted)">
          {{ field.name }}: <EventFieldValue :field-type="field.fieldType" :value="field.value"/>
        </span>
      </div>
      <div class="flex items-center gap-2">
        <EditButton @click="emit('edit')"/>
        <DeleteButton @click="emit('remove')"/>
      </div>
    </NeutralContainer>
  </RowLink>
</template>
