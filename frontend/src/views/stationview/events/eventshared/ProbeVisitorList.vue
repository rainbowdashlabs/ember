/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import type {WaitingListVisitor} from '@/api/generated/schema'
import RowLink from '@/components/navigation/RowLink.vue'
import {formatTime} from '@/util/format'

/**
 * Who from the waiting lists comes to one date for a probe: the name leading to their entry, the time
 * they are expected and the list they wait on.
 */
defineProps<{
  visitors: WaitingListVisitor[]
}>()

const {t} = useI18n()

function entryPage(visitor: WaitingListVisitor): RouteLocationRaw {
  return {name: 'waiting-list-entry', params: {id: visitor.listId, entryId: visitor.entryId}}
}
</script>

<template>
  <ul class="space-y-1 text-sm">
    <li v-for="visitor in visitors" :key="visitor.entryId" :data-testid="`probe-visitor-${visitor.entryId}`">
      <RowLink :to="entryPage(visitor)">
        <span class="text-primary hover:underline">{{ visitor.firstname }} {{ visitor.lastname }}</span>
      </RowLink>
      <span v-if="visitor.arrivalTime" class="ml-2">
        {{ t('eventDetail.probeVisitorArrival', {time: formatTime(visitor.arrivalTime)}) }}
      </span>
      <span class="ml-2 text-(--text-muted)">{{ t('eventDetail.probeVisitorFrom', {list: visitor.listName}) }}</span>
    </li>
  </ul>
</template>
