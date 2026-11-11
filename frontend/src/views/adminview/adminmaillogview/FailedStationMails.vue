/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import {useDataTable} from '@/composables/useDataTable'
import type {StationFailedMails} from '@/api/generated/schema'
import {stationFailedMailColumns} from './failedMailColumns'

/**
 * The stations' mails that did not arrive, one row per station: how many and why, never whom they
 * were for or what they said. Taking note of a station reaches the failures the row shows and no
 * later one, and leaves what the station sees on its own mail page alone.
 */
const props = defineProps<{
  stations: readonly StationFailedMails[]
  busy: boolean
}>()

const emit = defineEmits<{
  acknowledge: [group: StationFailedMails]
}>()

const {t} = useI18n()

const table = useDataTable<StationFailedMails>({
  id: 'admin-failed-station-mails',
  perStation: false,
  rows: () => props.stations,
  columns: computed(() => stationFailedMailColumns(t)),
  rowKey: group => group.stationUid,
})
</script>

<template>
  <div class="space-y-2">
    <SubHeader>{{ t('failedMails.stationsTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('failedMails.stationsHint') }}</MutedText>
    <RecordTable :table="table" plain test-id="failed-station-mails" row-test-id="failed-station">
      <template #actions="{row}">
        <SecondaryButton :disabled="busy" :icon="['fas', 'check']" data-testid="failed-station-acknowledge" @click="emit('acknowledge', row)">
          {{ t('failedMails.acknowledge') }}
        </SecondaryButton>
      </template>
    </RecordTable>
  </div>
</template>
