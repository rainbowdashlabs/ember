/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {stations} from '@/api'
import {ReminderResult, type AdminStationSummary} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorStatus} from '@/util/apiError'
import type {Failure} from '@/util/failure'
import {showToast} from '@/util/toast'
import {canRemind, HAND_GAP_MS, lastReminderText, remindedByHandJustNow} from './stationList'

/**
 * The reminder to the manager of a station that waits, and when the last one went out. A manager who
 * cannot sign in yet is sent a fresh link, one who can is told the station waits to be set up; the
 * server decides which. The station as it stands after the reminder is handed to `onReminded`, which
 * is a prop so a list that redraws the row meanwhile still hears it; so is the station read afresh
 * when the server answers that a reminder went out only minutes ago.
 *
 * `compact` draws the button alone, for a table row: the row shows the last reminder in a column of
 * its own, and a refusal goes to `onFailure` for the one alert above the table.
 */
const props = defineProps<{
  station: AdminStationSummary
  compact?: boolean
  onReminded?: (station: AdminStationSummary) => void
  onFailure?: (failure: Failure | null) => void
}>()

const {t} = useI18n()

const {running, failure, run: remind} = useAsyncAction(async () => {
  try {
    const answer = await stations.remindManager(props.station.id)
    props.onReminded?.(answer.station)
    showToast(t(answer.result === ReminderResult.SENT
        ? 'adminStations.reminderSent'
        : 'adminStations.reminderConfirmedWithoutMail'), 'success')
  } catch (e) {
    if (apiErrorStatus(e) === 429) await catchUp()
    throw e
  }
})

/** Reads the station again after a refusal, without letting a failure there hide the refusal itself. */
async function catchUp() {
  try {
    props.onReminded?.(await stations.getAdminStation(props.station.id))
  } catch {
    return
  }
}

watch(failure, value => props.onFailure?.(value))

const lastReminder = computed(() => lastReminderText(props.station.lastReminder, t))
const now = ref(Date.now())
const justReminded = computed(() => remindedByHandJustNow(props.station.lastReminder, now.value))
let reopen: ReturnType<typeof setTimeout> | undefined

/**
 * Opens the button again once the ten minutes after a reminder by hand are over. The time is not
 * reactive, so a timer moves it on at the one moment that changes anything.
 */
watch(() => props.station.lastReminder, reminder => {
  clearTimeout(reopen)
  now.value = Date.now()
  if (!reminder || !remindedByHandJustNow(reminder, now.value)) return
  const left = HAND_GAP_MS - (now.value - Date.parse(reminder.sentAt))
  reopen = setTimeout(() => {
    now.value = Date.now()
  }, left + 1)
}, {immediate: true})

onBeforeUnmount(() => clearTimeout(reopen))
</script>

<template>
  <div v-if="canRemind(station) || (!compact && lastReminder)" class="space-y-1">
    <SecondaryButton
        v-if="canRemind(station)"
        :icon="['fas', 'paper-plane']"
        :disabled="running || justReminded"
        :title="justReminded ? t('adminStations.remindedJustNow') : undefined"
        data-testid="remind-manager"
        @click="remind()">
      {{ t('adminStations.remind') }}
    </SecondaryButton>
    <template v-if="!compact">
      <MutedText v-if="lastReminder" tag="p" data-testid="last-reminder">{{ lastReminder }}</MutedText>
      <MutedText v-if="justReminded" tag="p" data-testid="reminded-just-now">{{ t('adminStations.remindedJustNow') }}</MutedText>
      <FailureAlert :failure="failure"/>
    </template>
  </div>
</template>
