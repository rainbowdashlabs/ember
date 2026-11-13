/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import RowLink from '@/components/navigation/RowLink.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {EventGroupSummary} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {formatDateTime} from '@/util/format'

/** One event group in the list: its name, how many parts and places it has, and when sign-ups close. */
defineProps<{
  eventGroup: EventGroupSummary
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()
</script>

<template>
  <RowLink :to="{name: 'event-group', params: {eventGroupId: eventGroup.id}}">
    <NeutralContainer class="flex flex-wrap items-center justify-between gap-2 hover:bg-(--bg-accent)" data-testid="event-group-row">
      <div class="min-w-0">
        <p class="font-medium truncate">{{ eventGroup.name }}</p>
        <MutedText tag="p" size="sm">
          {{ t('eventGroups.partCount', {count: eventGroup.partCount}) }}
          <template v-if="eventGroup.registrationDeadline"> · {{ t('eventGroups.deadline', {date: formatDateTime(eventGroup.registrationDeadline, stationTimezone)}) }}</template>
        </MutedText>
      </div>
      <div class="flex items-center gap-2 shrink-0">
        <ErrorBadge v-if="eventGroup.cancelled">{{ t('eventGroups.cancelled') }}</ErrorBadge>
        <MutedText tag="span" size="sm">
          {{ eventGroup.registrationLimit != null
            ? t('eventGroups.places', {holding: eventGroup.holding, limit: eventGroup.registrationLimit})
            : t('eventGroups.placesOpen', {holding: eventGroup.holding}) }}
        </MutedText>
      </div>
    </NeutralContainer>
  </RowLink>
</template>
