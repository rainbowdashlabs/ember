/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {events} from '@/api'
import type {EventLink, EventLinks} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import {useEventRoutes} from '@/composables/useEventRoutes'

const props = defineProps<{
  eventId: number
}>()

const eventRoutes = useEventRoutes()
const links = ref<EventLinks | null>(null)

watch(() => props.eventId, async id => {
  links.value = await events.getEventLinks(id).catch(() => null)
}, {immediate: true})

/** Where a linked appointment opens: on the date the link names. */
function target(link: EventLink) {
  return {name: eventRoutes.detailOnDate, params: {id: link.eventId, date: link.date}}
}

/** How a link this appointment gave a date to reads: the series going on, or a date taken out. */
function givenAway(link: EventLink): string {
  return link.series ? 'events.links.continuedAs' : 'events.links.detached'
}
</script>

<template>
  <div v-if="links && (links.continues || links.continuations.length > 0)" class="space-y-1 text-sm text-(--text-muted)">
    <i18n-t v-if="links.continues" keypath="events.links.continues" tag="p">
      <template #name>
        <NuxtLink :to="target(links.continues)" class="underline">{{ links.continues.name }}</NuxtLink>
      </template>
      <template #date>{{ formatDate(links.continues.date) }}</template>
    </i18n-t>
    <i18n-t v-for="link in links.continuations" :key="link.eventId" :keypath="givenAway(link)" tag="p">
      <template #name>
        <NuxtLink :to="target(link)" class="underline">{{ link.name }}</NuxtLink>
      </template>
      <template #date>{{ formatDate(link.date) }}</template>
    </i18n-t>
  </div>
</template>
