/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {waitingList} from '@/api'
import type {WaitingListVisitor} from '@/api/generated/schema'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import {formatTime} from '@/util/format'

/**
 * Who from the waiting lists comes to this date for a probe. They have joined nothing and stand on no
 * attendee list, so this is how whoever runs the evening learns they come. Shown only to those who
 * may read the waiting lists, and only where somebody comes.
 */
const props = defineProps<{
  eventId: number
  date: string
}>()

const {t} = useI18n()

const visitors = ref<WaitingListVisitor[]>([])

watch(() => [props.eventId, props.date] as const, async ([eventId, date]) => {
  try {
    visitors.value = await waitingList.visitors(eventId, date)
  } catch {
    visitors.value = []
  }
}, {immediate: true})

function entryPage(visitor: WaitingListVisitor): RouteLocationRaw {
  return {name: 'waiting-list-entry', params: {id: visitor.listId, entryId: visitor.entryId}}
}
</script>

<template>
  <NeutralContainer v-if="visitors.length > 0" class="space-y-2" data-testid="probe-visitors">
    <SubHeader>{{ t('eventDetail.probeVisitors') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('eventDetail.probeVisitorsHint') }}</MutedText>
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
  </NeutralContainer>
</template>
