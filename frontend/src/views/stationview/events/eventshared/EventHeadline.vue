/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import EventWhen from './EventWhen.vue'

/**
 * The head of an appointment wherever one is listed: when it is, then what it is called.
 *
 * <p>Shared by the station's own appointments and by those of partner stations, because a reader
 * scanning one list should not have to learn two layouts. Whatever else belongs beside the name goes
 * in the slot: the category, the lock, the partner's name, the note that it has to be signed up for.
 */
defineProps<{
  name?: string
  to: RouteLocationRaw
  date: string
  endDate: string | null
  startTime?: string
  endTime?: string
  formatTime: (iso?: string) => string
}>()
</script>

<template>
  <div>
    <EventWhen
        :date="date" :end-date="endDate" :start-time="startTime" :end-time="endTime"
        :format-time="formatTime"/>
    <div class="flex flex-wrap items-center gap-x-2">
      <router-link :to="to" class="font-medium text-primary hover:underline">{{ name }}</router-link>
      <slot/>
    </div>
  </div>
</template>
