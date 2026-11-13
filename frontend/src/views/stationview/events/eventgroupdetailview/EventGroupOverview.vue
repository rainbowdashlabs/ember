/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import EventGroupFields from './EventGroupFields.vue'
import type {EventGroupDetail} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {asksForAll} from '@/util/eventGroupChoice'
import {formatDateTime} from '@/util/format'
import {renderMarkdown} from '@/util/markdown'

/**
 * What an event group is: its description, places, deadline and confirmation, and its parts grouped by set
 * with how many of each set a sign-up has to choose. A part links to its appointment.
 */
const props = defineProps<{
  detail: EventGroupDetail
  /** Where a part leads; to the station's own appointment unless a partner's event group page says otherwise. */
  partLink?: (eventId: number) => RouteLocationRaw
}>()

function linkOf(eventId: number): RouteLocationRaw {
  return props.partLink ? props.partLink(eventId) : {name: 'event-detail', params: {id: eventId}}
}

const {t} = useI18n()
const {stationTimezone} = useSession()

const eventGroup = computed(() => props.detail.eventGroup)

/** The parts of a set in date order. */
function partsOf(setId: number) {
  return props.detail.parts.filter(part => part.setId === setId)
}

/** The rule a set states, "all mandatory" or "at least n of m". */
function ruleOf(setId: number): string {
  const set = props.detail.sets.find(candidate => candidate.id === setId)!
  if (asksForAll(set, props.detail.parts)) return t('eventGroups.setAll')
  return t('eventGroups.setRule', {minimum: set.minimum, count: partsOf(setId).length})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="event-group-overview">
    <div class="flex flex-wrap items-center gap-2">
      <ErrorBadge v-if="eventGroup.cancelled">
        {{ eventGroup.cancelReason ? t('eventGroups.cancelledReason', {reason: eventGroup.cancelReason}) : t('eventGroups.cancelled') }}
      </ErrorBadge>
      <InfoBadge v-if="eventGroup.registrationDeadline">
        {{ t('eventGroups.deadline', {date: formatDateTime(eventGroup.registrationDeadline, stationTimezone)}) }}
      </InfoBadge>
      <MutedText tag="span" size="sm">
        {{ eventGroup.registrationLimit != null
          ? t('eventGroups.places', {holding: eventGroup.holding, limit: eventGroup.registrationLimit})
          : t('eventGroups.placesOpen', {holding: eventGroup.holding}) }}
      </MutedText>
      <MutedText v-if="eventGroup.requiresConfirmation" tag="span" size="sm">{{ t('eventGroups.confirmation') }}</MutedText>
    </div>
    <ProseContent v-if="eventGroup.description" v-html="renderMarkdown(eventGroup.description)"/>
    <EventGroupFields :fields="detail.fields"/>
    <SectionHeader>{{ t('eventGroups.parts') }}</SectionHeader>
    <MutedText v-if="detail.parts.length === 0" tag="p" size="sm">{{ t('eventGroups.noParts') }}</MutedText>
    <section v-for="set in detail.sets" :key="set.id" class="space-y-2" :data-testid="`event-group-set-${set.id}`">
      <SubHeader>{{ set.name }} <MutedText tag="span" size="sm">({{ ruleOf(set.id) }})</MutedText></SubHeader>
      <ul class="space-y-1">
        <li v-for="part in partsOf(set.id)" :key="part.eventId">
          <RowLink :to="linkOf(part.eventId)" class="flex flex-wrap items-center gap-2 text-sm">
            <span :class="{'line-through text-(--text-muted)': part.calledOff}">{{ part.name }}</span>
            <MutedText tag="span" size="sm">{{ formatDateTime(part.startTime, stationTimezone) }}</MutedText>
            <ErrorBadge v-if="part.calledOff">{{ t('eventGroups.partCalledOff') }}</ErrorBadge>
            <MutedText v-else-if="part.tookPlace" tag="span" size="sm">{{ t('eventGroups.partPast') }}</MutedText>
          </RowLink>
        </li>
      </ul>
    </section>
  </NeutralContainer>
</template>
