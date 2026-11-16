/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EventGroupPartBadge from './EventGroupPartBadge.vue'
import type {EventGroupPartSummary} from '@/api/generated/schema'

/**
 * Tells the reader of an appointment that it is a part of an event group, and where the sign-up for it
 * and the settings that come with it live: on the event group.
 */
const props = defineProps<{
  eventGroup: EventGroupPartSummary
  /** What the notice says beside the event group: the sign-up on the page, or the locked settings in the editor. */
  message: string
  /** Where the event group is opened; this station's event group page unless a partner holds it. */
  to?: RouteLocationRaw
}>()

const eventGroupLink = computed<RouteLocationRaw>(() =>
    props.to ?? {name: 'event-group', params: {eventGroupId: props.eventGroup.eventGroupId}})

const {t} = useI18n()
</script>

<template>
  <Alert variant="info" data-testid="event-group-part-notice">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div class="space-y-1">
        <p>{{ t('eventGroups.partOf', {eventGroup: eventGroup.eventGroupName}) }} <EventGroupPartBadge :event-group="eventGroup"/></p>
        <p>{{ message }}</p>
      </div>
      <SecondaryButton :icon="['fas', 'graduation-cap']" :to="eventGroupLink">
        {{ t('eventGroups.openEventGroup') }}
      </SecondaryButton>
    </div>
  </Alert>
</template>
