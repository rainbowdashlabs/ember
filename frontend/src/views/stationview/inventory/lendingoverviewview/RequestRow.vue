/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {RouterLink} from 'vue-router'
import type {LendingRequestResponse} from '@/api/generated/schema'
import MutedText from '@/components/typography/MutedText.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {formatDate} from '@/util/format'
import LendingStatusBadge from '../lendingrequestview/LendingStatusBadge.vue'

/**
 * One request in a block of the overview: the other station, the days and what it asks for, opening
 * the request. The slot after it takes what can be done right there.
 */
const props = defineProps<{
  entry: LendingRequestResponse
}>()

defineSlots<{
  actions(): unknown
}>()

const routes = useInventoryRoutes()
const station = computed(() => props.entry.isOwner ? props.entry.requestingStationName : props.entry.owningStationName)
const days = computed(() => {
  const {requestedDateFrom: from, requestedDateTo: to} = props.entry.request
  return to && to !== from ? `${formatDate(from)} - ${formatDate(to)}` : formatDate(from)
})
const link = computed(() => routes.lendingRequest ? {name: routes.lendingRequest, params: {id: props.entry.request.id}} : null)
</script>

<template>
  <li class="flex flex-wrap items-center gap-2 py-2" :class="entry.overdue ? 'text-error' : ''" data-testid="lending-overview-row">
    <component :is="link ? RouterLink : 'span'" :to="link || undefined" class="min-w-0 flex-1 hover:underline">
      <span class="font-medium">{{ station }}</span>
      <MutedText class="ml-2">{{ days }}</MutedText>
      <MutedText v-if="entry.itemSummary" tag="p" class="truncate">{{ entry.itemSummary }}</MutedText>
    </component>
    <LendingStatusBadge :status="entry.request.status" :overdue="entry.overdue"/>
    <slot name="actions"/>
  </li>
</template>
