/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type { WaitingListEntryWithScore } from '@/api/generated/schema'

const props = defineProps<{
  item: WaitingListEntryWithScore
}>()

const emit = defineEmits<{
  invite: [entryId: number]
  offerOtherDates: [entryId: number]
  backToWaiting: [entryId: number]
  moveToTesting: [entryId: number]
  withdraw: [entryId: number]
  navigateToEntry: [entryId: number]
  deleteEntry: [entry: WaitingListEntryWithScore]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-end gap-1">
    <IconButton
      v-if="props.item.entry.status === 'WAITING'"
      icon="paper-plane"
      :label="t('waitingList.invite')"
      @click.stop="emit('invite', props.item.entry.id)"
    />
    <IconButton
      v-if="props.item.entry.status === 'INVITED'"
      icon="calendar-days"
      :label="t('waitingList.offerOtherDates')"
      data-testid="waitlist-offer-other-dates"
      @click.stop="emit('offerOtherDates', props.item.entry.id)"
    />
    <IconButton
      v-if="props.item.entry.status === 'INVITED'"
      icon="rotate-left"
      :label="t('waitingList.backToWaiting')"
      @click.stop="emit('backToWaiting', props.item.entry.id)"
    />
    <IconButton
      v-if="props.item.entry.status === 'INVITED'"
      icon="play"
      :label="t('waitingList.startTesting')"
      @click.stop="emit('moveToTesting', props.item.entry.id)"
    />
    <IconButton
      v-if="props.item.entry.status === 'INVITED'"
      icon="circle-xmark"
      :label="t('waitingList.withdraw')"
      data-testid="waitlist-withdraw-invited"
      @click.stop="emit('withdraw', props.item.entry.id)"
    />
    <EditButton @click.stop="emit('navigateToEntry', props.item.entry.id)" />
    <DeleteButton @click.stop="emit('deleteEntry', props.item)" />
  </div>
</template>
