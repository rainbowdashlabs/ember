/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {useEventRoutes} from '@/composables/useEventRoutes'
import RowLink from '@/components/navigation/RowLink.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {EventGroupSummary} from '@/api/generated/schema'
import {eventGroups} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useSession} from '@/composables/useSession'
import {formatDate} from '@/util/format'

/**
 * Every event group meant for the reader, each leading to its page: its name, the days it runs from
 * and to, how many appointments it holds, how many sign-ups hold a place, wait for a decision or wait
 * on the waiting list, and whether it was cancelled. This is the one place an event group is found
 * without going through one of its appointments, and a new one is started from here as well.
 */
const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()

const {stationTimezone} = useSession()

const groups = ref<EventGroupSummary[]>([])

/** The days an event group runs, one day where it starts and ends on it, nothing while it has no appointment. */
function daysOf(group: EventGroupSummary): string {
  const first = formatDate(group.firstStart, stationTimezone.value)
  const last = formatDate(group.lastEnd, stationTimezone.value)
  if (!first || !last) return ''
  return first === last ? first : t('eventGroups.dateRange', {first, last})
}

const {loading, failure} = useAsyncLoader(async () => {
  groups.value = await eventGroups.listEventGroups()
})
</script>

<template>
  <div class="space-y-3" data-testid="event-group-list">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('events.tabEventGroups') }}</SectionHeader>
      <SecondaryButton
          v-if="eventRoutes.eventGroupCreate"
          :icon="['fas', 'layer-group']"
          data-testid="event-group-list-create"
          @click="router.push({name: eventRoutes.eventGroupCreate})"
      >
        {{ t('eventGroups.create') }}
      </SecondaryButton>
    </div>
    <Spinner v-if="loading" size="lg"/>
    <FailureAlert :failure="failure"/>
    <MutedText v-if="!loading && !failure && groups.length === 0" tag="p" size="sm">{{ t('eventGroups.listEmpty') }}</MutedText>
    <ul class="space-y-2">
      <li v-for="group in groups" :key="group.id">
        <RowLink :to="{name: 'event-group', params: {eventGroupId: group.id}}">
          <NeutralContainer class="flex flex-wrap items-center justify-between gap-2 hover:bg-(--bg-accent)" :data-testid="`event-group-list-row-${group.id}`">
            <div class="min-w-0">
              <p class="font-medium truncate" :class="{'line-through text-(--text-muted)': group.cancelled}">{{ group.details.name }}</p>
              <MutedText v-if="daysOf(group)" tag="p" size="sm" :data-testid="`event-group-list-days-${group.id}`">{{ daysOf(group) }}</MutedText>
              <MutedText tag="p" size="sm">
                {{ t('eventGroups.listCounts', {parts: group.partCount, holding: group.holding, pending: group.pending, queued: group.queued}) }}
              </MutedText>
            </div>
            <ErrorBadge v-if="group.cancelled">{{ t('eventGroups.cancelled') }}</ErrorBadge>
          </NeutralContainer>
        </RowLink>
      </li>
    </ul>
  </div>
</template>
