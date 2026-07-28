/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import EventFieldValue from '../eventshared/EventFieldValue.vue'
import type {EventField, StationEvent} from '@/api/events'

defineProps<{
  event: StationEvent
  detailRoute: RouteLocationRaw
  overviewFields: EventField[]
  formatTime: (iso?: string) => string
  showAttendance: boolean
}>()

const emit = defineEmits<{
  attendance: [event: StationEvent]
}>()

const {t} = useI18n()
</script>

<template>
  <PrimaryContainer class="space-y-2">
    <div class="flex items-center justify-between">
      <router-link :to="detailRoute" class="font-semibold text-primary hover:underline">{{ event.name }}</router-link>
      <MutedIcon v-if="event.restricted" :icon="['fas', 'lock']" class="ml-1"/>
      <span class="text-sm">{{ formatTime(event.startTime) }} – {{ formatTime(event.endTime) }}</span>
    </div>
    <p v-if="event.description" class="text-sm text-(--text-muted)">{{ event.description }}</p>
    <div v-if="overviewFields.length" class="flex flex-wrap gap-3 text-xs">
      <span v-for="f in overviewFields" :key="f.id" class="text-(--text-muted)">
        <span class="font-medium">{{ f.name }}:</span>
        <EventFieldValue :field-type="f.fieldType" :value="f.value"/>
      </span>
    </div>
    <div class="flex gap-2">
      <PrimaryButton v-if="showAttendance" :icon="['fas', 'clipboard-user']" @click="emit('attendance', event)">
        {{ t('eventsUpcoming.attendance') }}
      </PrimaryButton>
    </div>
  </PrimaryContainer>
</template>
