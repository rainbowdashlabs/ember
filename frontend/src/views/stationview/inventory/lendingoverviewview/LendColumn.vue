/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import OverviewBlock from './OverviewBlock.vue'
import RequestRow from './RequestRow.vue'
import type {LendBlocks} from './overviewBlocks'

/** What the station lends: requests to answer, gear to hand out, gear out, and gear overdue. */
defineProps<{
  blocks: LendBlocks
  busy?: boolean
}>()

const emit = defineEmits<{
  approve: [requestId: number]
  decline: [requestId: number]
  remind: [requestId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-5" data-testid="lending-overview-lend">
    <SectionHeader>{{ t('lendingOverview.lend') }}</SectionHeader>
    <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.toAnswer')" :count="blocks.toAnswer.length" :empty="t('lendingOverview.toAnswerEmpty')">
      <RequestRow v-for="entry in blocks.toAnswer.slice(0, limit)" :key="entry.request.id" :entry="entry">
        <template #actions>
          <ButtonRow pair>
            <SuccessButton :icon="['fas', 'check']" :disabled="busy" :aria-label="t('lendingOverview.approveFrom', {station: entry.requestingStationName})"
                           @click="emit('approve', entry.request.id)">{{ t('lending.approve') }}</SuccessButton>
            <ErrorButton :icon="['fas', 'xmark']" :disabled="busy" :aria-label="t('lendingOverview.declineFrom', {station: entry.requestingStationName})"
                         @click="emit('decline', entry.request.id)">{{ t('lending.decline') }}</ErrorButton>
          </ButtonRow>
        </template>
      </RequestRow>
    </OverviewBlock>
    <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.handOutSoon')" :count="blocks.handOutSoon.length" :empty="t('lendingOverview.handOutSoonEmpty')">
      <RequestRow v-for="entry in blocks.handOutSoon.slice(0, limit)" :key="entry.request.id" :entry="entry"/>
    </OverviewBlock>
    <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.outNow')" :count="blocks.outNow.length" :empty="t('lendingOverview.outNowEmpty')">
      <RequestRow v-for="entry in blocks.outNow.slice(0, limit)" :key="entry.request.id" :entry="entry"/>
    </OverviewBlock>
    <OverviewBlock v-slot="{limit}" :title="t('lendingOverview.overdue')" :count="blocks.overdue.length" :empty="t('lendingOverview.overdueEmpty')">
      <RequestRow v-for="entry in blocks.overdue.slice(0, limit)" :key="entry.request.id" :entry="entry">
        <template #actions>
          <SecondaryButton :icon="['fas', 'bell']" :disabled="busy" :aria-label="t('lendingOverview.remindFor', {station: entry.requestingStationName})"
                           @click="emit('remind', entry.request.id)">{{ t('lendingReturn.remind') }}</SecondaryButton>
        </template>
      </RequestRow>
    </OverviewBlock>
  </div>
</template>
