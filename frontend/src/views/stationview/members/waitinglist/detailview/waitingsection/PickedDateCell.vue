/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'
import {formatDate, formatTime} from '@/util/format'
import {pickedOption} from '../../pickedDate'

/** The date somebody picked to come to the probe, with the appointment and the time they were asked for. */
const props = defineProps<{entry: WaitingListEntryWithScore['entry']}>()

const {t} = useI18n()

const picked = computed(() => pickedOption(props.entry))
</script>

<template>
  <span v-if="picked" class="whitespace-nowrap" :data-testid="`waitlist-picked-${entry.id}`">
    {{ formatDate(picked.date) }}
    <span v-if="picked.arrivalTime"> {{ t('waitingList.pickedDateAt', {time: formatTime(picked.arrivalTime)}) }}</span>
    <span class="block text-xs text-(--text-muted)">{{ picked.appointmentName }}</span>
  </span>
</template>
