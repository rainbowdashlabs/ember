/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {formatDate} from '@/util/format'
import OverviewBlock from './OverviewBlock.vue'
import RequestRow from './RequestRow.vue'
import type {BorrowBlocks, BorrowedGroup} from './overviewBlocks'

/**
 * What the station borrows: requests waiting for an answer, gear to pick up, partner gear at the
 * station, and gear to give back. The requests show to whoever may ask for gear, the gear at the
 * station to whoever may read the inventory; whoever may only read it sees all of that gear at once,
 * since it is the whole page to them and its requests are not theirs to open.
 */
const props = defineProps<{
  blocks: BorrowBlocks | null
  borrowed: BorrowedGroup[]
  /** Whether the reader may read the inventory, which is what shows the gear at the station. */
  reads: boolean
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()

function requestLink(id: number) {
  return props.blocks && routes.lendingRequest ? {name: routes.lendingRequest, params: {id}} : undefined
}
</script>

<template>
  <div class="space-y-5" data-testid="lending-overview-borrow">
    <SectionHeader>{{ t('lendingOverview.borrow') }}</SectionHeader>
    <template v-if="blocks">
      <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.waiting')" :count="blocks.waiting.length" :empty="t('lendingOverview.waitingEmpty')">
        <RequestRow v-for="entry in blocks.waiting.slice(0, limit)" :key="entry.request.id" :entry="entry"/>
      </OverviewBlock>
      <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.pickUpSoon')" :count="blocks.pickUpSoon.length" :empty="t('lendingOverview.pickUpSoonEmpty')">
        <RequestRow v-for="entry in blocks.pickUpSoon.slice(0, limit)" :key="entry.request.id" :entry="entry"/>
      </OverviewBlock>
    </template>
    <OverviewBlock v-if="reads" v-slot="{limit}" :title="t('lendingOverview.withUs')" :count="borrowed.length" :empty="t('lendingOverview.withUsEmpty')"
                   :whole="!blocks">
      <li v-for="group in borrowed.slice(0, limit)" :key="group.requestId" class="py-2" data-testid="lending-overview-borrowed">
        <component :is="requestLink(group.requestId) ? RouterLink : 'span'" :to="requestLink(group.requestId)" class="hover:underline">
          <span class="font-medium">{{ group.stationName }}</span>
          <MutedText class="ml-2">{{ t('lendingOverview.until', {day: formatDate(group.dueOn)}) }}</MutedText>
        </component>
        <MutedText tag="p">{{ group.pieces.map(piece => piece.internalId ? `${piece.name} (${piece.internalId})` : piece.name).join(', ') }}</MutedText>
      </li>
    </OverviewBlock>
    <OverviewBlock v-if="blocks" v-slot="{limit}" :title="t('lendingOverview.giveBackSoon')" :count="blocks.giveBackSoon.length"
                   :empty="t('lendingOverview.giveBackSoonEmpty')">
      <RequestRow v-for="entry in blocks.giveBackSoon.slice(0, limit)" :key="entry.request.id" :entry="entry"/>
    </OverviewBlock>
    <MutedText v-if="reads && !blocks" tag="p">{{ t('lendingOverview.snapshotNote') }}</MutedText>
  </div>
</template>
