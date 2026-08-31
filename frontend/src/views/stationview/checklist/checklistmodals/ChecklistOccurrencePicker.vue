/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from '@/components/input/search/EntitySearchPicker.vue'
import {listUpcomingOccurrences, type UpcomingEventOccurrence} from '@/api/events'
import type {ChecklistSourceRequest} from '@/api/checklists'
import {formatDate} from '@/util/format'

/**
 * Picks the one evening a list should follow.
 *
 * <p>It offers occurrences rather than appointments, because that is what a sign-up is kept
 * against: a weekly Dienst named without a date would mean every Tuesday there has ever been. And
 * it offers only what the reader may know about, since the list of upcoming occurrences is already
 * narrowed to the appointments their view audience lets them see.
 */
const model = defineModel<ChecklistSourceRequest | null>({required: true})

const props = defineProps<{
  /** What the chip should read for an evening picked before this screen was opened. */
  selectedDisplay?: string | null
  disabled?: boolean
}>()

const {t} = useI18n()

const pickedLabel = ref<string | null>(null)

/**
 * An occurrence has no id of its own, so the appointment and the date together are its key.
 * Clearing the chip is the only write the picker makes through this, which is why the setter only
 * has to answer for null.
 */
const key = computed({
  get: () => (model.value ? `${model.value.eventId}|${model.value.date}` : null),
  set: (value: string | null) => {
    if (!value) {
      model.value = null
      pickedLabel.value = null
    }
  },
})

async function search(query: string): Promise<UpcomingEventOccurrence[]> {
  return listUpcomingOccurrences({
    requiresRegistration: true,
    search: query || undefined,
    limit: 10,
  })
}

function label(occurrence: UpcomingEventOccurrence): string {
  return occurrence.event.name ?? ''
}

function subtitle(occurrence: UpcomingEventOccurrence): string {
  return formatDate(occurrence.date)
}

function pick(occurrence: UpcomingEventOccurrence) {
  model.value = {eventId: occurrence.event.id, date: occurrence.date}
  pickedLabel.value = `${label(occurrence)} ${t('checklist.occurrenceOn', {date: subtitle(occurrence)})}`
}
</script>

<template>
  <EntitySearchPicker
      v-model="key"
      :search-fn="search"
      :display-fn="label"
      :subtitle-fn="subtitle"
      :key-fn="(o: UpcomingEventOccurrence) => `${o.event.id}|${o.date}`"
      :icon-fn="() => ['fas', 'calendar-days']"
      :selected-display="pickedLabel ?? props.selectedDisplay"
      :placeholder="t('checklist.occurrencePlaceholder')"
      :empty-label="t('checklist.occurrenceEmpty')"
      :disabled="props.disabled"
      data-testid="checklist-occurrence-picker"
      @pick="pick"
  />
</template>
