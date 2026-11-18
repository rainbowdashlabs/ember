/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import EventGroupTile from '@/views/stationview/events/eventshared/EventGroupTile.vue'
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import EventGroupFields from './EventGroupFields.vue'
import type {EventGroupDetail, EventGroupPartState} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {eventGroupPlaces, partPlaces, partsOfSet, ruleOf} from '@/util/eventGroupChoice'
import {placesLabel} from '@/util/eventPlaces'
import {formatDate, formatDateTime, formatTime} from '@/util/format'
import type {EventGroupMark} from '../eventshared/eventGroupColor'
import {renderMarkdown} from '@/util/markdown'

/**
 * What an event group is: its description, places, deadline and confirmation, and its parts grouped by set
 * with how many of each set a sign-up has to choose. A part links to its appointment and says how its
 * own places stand where it has a limit.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Where a part leads; to the station's own appointment unless a partner's event group page says otherwise. */
  partLink?: (eventId: number) => RouteLocationRaw
  /** The reader, where they may put themselves into the event group's member fields. */
  currentMemberId?: number
  /** The station's members by id, for the member fields. */
  memberNames?: Map<number, string>
  /** Called once the reader stepped into or out of a member field. */
  onChanged?: () => void
}>()

function linkOf(eventId: number): RouteLocationRaw {
  return props.partLink ? props.partLink(eventId) : {name: 'event-detail', params: {id: eventId}}
}

const {t} = useI18n()
const {stationTimezone} = useSession()

const eventGroup = computed(() => props.detail.eventGroup)

/** How the places for people stand, null where the event group limits no people. */
const peoplePlaces = computed(() => eventGroupPlaces(eventGroup.value))

/** The minimum of accepted sign-ups the event group needs and by when, or nothing where it has none. */
const minimumRule = computed(() => {
  const minimum = eventGroup.value.details.minRegistrations
  const days = eventGroup.value.details.thresholdDays
  if (!minimum || days == null) return null
  return days === 0
    ? t('eventGroups.minimumRuleSameDay', {minimum})
    : t('eventGroups.minimumRule', {minimum, days})
})

/** The event group as its bar names it, running from the start of its first part to the end of its last. */
const mark = computed<EventGroupMark>(() => {
  const parts = props.detail.parts
  const first = parts.reduce<string | undefined>((earliest, part) => !earliest || part.startTime < earliest ? part.startTime : earliest, undefined)
  const last = parts.reduce<string | undefined>((latest, part) => !latest || part.endTime > latest ? part.endTime : latest, undefined)
  return {
    eventGroupId: eventGroup.value.id,
    eventGroupName: eventGroup.value.details.name,
    color: eventGroup.value.details.color,
    firstStart: first,
    lastEnd: last,
  }
})

/** When a part runs, from its start to its end; the end of a part that ends on the day it starts is only its clock. */
function whenOf(part: EventGroupPartState): string {
  const sameDay = formatDate(part.startTime, stationTimezone.value) === formatDate(part.endTime, stationTimezone.value)
  return t('eventGroups.dateRange', {
    first: formatDateTime(part.startTime, stationTimezone.value),
    last: sameDay ? formatTime(part.endTime, stationTimezone.value) : formatDateTime(part.endTime, stationTimezone.value),
  })
}
</script>

<template>
  <EventGroupTile class="space-y-4" :event-group="mark" data-testid="event-group-overview">
    <div class="flex flex-wrap items-center gap-2">
      <ErrorBadge v-if="eventGroup.cancelled">
        {{ eventGroup.cancelReason ? t('eventGroups.cancelledReason', {reason: eventGroup.cancelReason}) : t('eventGroups.cancelled') }}
      </ErrorBadge>
      <InfoBadge v-if="eventGroup.details.registrationDeadline">
        {{ t('eventGroups.deadline', {date: formatDateTime(eventGroup.details.registrationDeadline, stationTimezone)}) }}
      </InfoBadge>
      <MutedText tag="span" size="sm" data-testid="event-group-places">
        {{ peoplePlaces ? placesLabel(peoplePlaces, t) : t('eventGroups.placesOpen', {holding: eventGroup.holding}) }}
      </MutedText>
      <MutedText v-if="!peoplePlaces && eventGroup.queued > 0" tag="span" size="sm" data-testid="event-group-waiting">
        {{ t('registrationQueue.waitingCount', {count: eventGroup.queued}) }}
      </MutedText>
      <MutedText v-if="eventGroup.details.requiresConfirmation" tag="span" size="sm">{{ t('eventGroups.confirmation') }}</MutedText>
    </div>
    <MutedText v-if="minimumRule" tag="p" size="sm" data-testid="event-group-minimum-rule">{{ minimumRule }}</MutedText>
    <ProseContent v-if="eventGroup.details.description" v-html="renderMarkdown(eventGroup.details.description)"/>
    <EventGroupFields
        :fields="detail.fields"
        :event-group-id="currentMemberId == null ? undefined : eventGroup.id"
        :current-member-id="currentMemberId"
        :member-names="memberNames"
        :on-changed="onChanged"
    />
    <SectionHeader>{{ t('eventGroups.parts') }}</SectionHeader>
    <MutedText v-if="detail.parts.length === 0" tag="p" size="sm">{{ t('eventGroups.noParts') }}</MutedText>
    <section v-for="set in detail.sets" :key="set.id" class="space-y-2" :data-testid="`event-group-set-${set.id}`">
      <SubHeader>{{ set.name }} <MutedText tag="span" size="sm">({{ ruleOf(set.id, detail.sets, detail.parts, t) }})</MutedText></SubHeader>
      <ul class="space-y-1">
        <li v-for="part in partsOfSet(set.id, detail.parts)" :key="part.eventId">
          <RowLink :to="linkOf(part.eventId)" class="flex flex-wrap items-center gap-2 text-sm">
            <span :class="{'line-through text-(--text-muted)': part.calledOff}">{{ part.name }}</span>
            <MutedText tag="span" size="sm">{{ whenOf(part) }}</MutedText>
            <MutedText v-if="partPlaces(part) && !part.calledOff" tag="span" size="sm" :data-testid="`event-group-part-places-${part.eventId}`">
              {{ placesLabel(partPlaces(part)!, t) }}
            </MutedText>
            <InfoBadge v-if="part.required && !part.calledOff" data-testid="event-group-part-required">{{ t('eventGroups.required') }}</InfoBadge>
            <ErrorBadge v-if="part.calledOff">{{ t('eventGroups.partCalledOff') }}</ErrorBadge>
            <MutedText v-else-if="part.tookPlace" tag="span" size="sm">{{ t('eventGroups.partPast') }}</MutedText>
          </RowLink>
        </li>
      </ul>
    </section>
  </EventGroupTile>
</template>
