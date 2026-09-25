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
  /**
   * Where the name leads, or nothing where the whole row is already the link to it. Two nested
   * links are one link too many: the reader hears the same destination twice and the markup is
   * invalid besides.
   */
  to?: RouteLocationRaw | null
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
      <router-link v-if="to" :to="to" class="font-medium text-primary hover:underline">{{ name }}</router-link>
      <span v-else class="font-medium text-primary hover:underline">{{ name }}</span>
      <slot/>
    </div>
  </div>
</template>
