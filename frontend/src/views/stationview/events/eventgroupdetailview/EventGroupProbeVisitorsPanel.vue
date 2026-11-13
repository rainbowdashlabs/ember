/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {waitingList} from '@/api'
import type {EventGroupPartState, WaitingListVisitor} from '@/api/generated/schema'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProbeVisitorList from '../eventshared/ProbeVisitorList.vue'
import {useSession} from '@/composables/useSession'
import {formatDate, stationDayOf} from '@/util/format'

/** One part of the event group with whoever comes to it for a probe. */
interface PartVisitors {
  part: EventGroupPartState
  visitors: WaitingListVisitor[]
}

/**
 * Who from the waiting lists comes to a part of the event group for a probe, part by part. A probe
 * visitor comes on one day, so they stay with the part they chose; this only gathers them here to
 * read, and each name leads to its waiting list entry. Shown only to those who may read the waiting
 * lists, and only where somebody comes.
 */
const props = defineProps<{
  parts: EventGroupPartState[]
}>()

const {t} = useI18n()
const {stationTimezone} = useSession()

const coming = ref<PartVisitors[]>([])
let latestLoad = 0

async function visitorsOf(part: EventGroupPartState): Promise<PartVisitors> {
  try {
    const day = stationDayOf(new Date(part.startTime), stationTimezone.value)
    return {part, visitors: await waitingList.visitors(part.eventId, day)}
  } catch {
    return {part, visitors: []}
  }
}

watch(() => props.parts, async parts => {
  const load = ++latestLoad
  const loaded = await Promise.all(parts.map(visitorsOf))
  if (load !== latestLoad) return
  coming.value = loaded.filter(entry => entry.visitors.length > 0)
}, {immediate: true})
</script>

<template>
  <NeutralContainer v-if="coming.length > 0" class="space-y-3" data-testid="event-group-probe-visitors">
    <SubHeader>{{ t('eventDetail.probeVisitors') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.probeVisitorsHint') }}</MutedText>
    <div v-for="entry in coming" :key="entry.part.eventId" class="space-y-1" :data-testid="`event-group-probe-part-${entry.part.eventId}`">
      <p class="text-sm font-medium">
        {{ t('eventGroups.probeVisitorsPart', {name: entry.part.name, date: formatDate(entry.part.startTime, stationTimezone)}) }}
      </p>
      <ProbeVisitorList :visitors="entry.visitors"/>
    </div>
  </NeutralContainer>
</template>
