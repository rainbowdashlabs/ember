/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {configOf, spanForWidth} from '@/components/profilefields/fieldLayout'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import PerDateFieldValue from './PerDateFieldValue.vue'
import PlaceMap from '@/components/map/PlaceMap.vue'
import IconButton from '@/components/button/IconButton.vue'
import {FieldType, type AppointmentField, type StationPlace} from '@/api/generated/schema'
import SelfRegistrationToggle from '../eventshared/SelfRegistrationToggle.vue'
import type {MemberLike} from '@/components/input/select/memberOption'
import {events, places} from '@/api'
import {normalisePlace} from '@/util/placeText'

const props = defineProps<{
  eventId: number
  fields: AppointmentField[]
  allMembers: MemberLike[]
  currentMemberId: number
  /** The attendance template, which takes a row of its own so the fields below line up beneath it. */
  templateName: string
  /** Which sheet the attendance is taken on is a setting, so only whoever sets it is shown it. */
  canEditEvent: boolean
  /** The occurrence on screen, which is the one a question answered per date is answered for. */
  effectiveDate?: string | null
}>()

const emit = defineEmits<{
  (e: 'field-updated', field: AppointmentField): void
}>()

const {t} = useI18n()

/** The station's members by id, under the name a reader knows them by. */
const memberNames = computed(() =>
    new Map(props.allMembers.map(m => [m.id, m.name ?? m.email ?? `#${m.id}`] as const)))

const pins = ref<StationPlace[]>([])

/** The key of every place field's text, so a reload happens only when a place changes. */
const placeKeys = computed(() => props.fields
    .filter(field => field.fieldType === FieldType.LOCATION && field.value.trim() !== '')
    .map(field => normalisePlace(field.value))
    .join('|'))

let pinRequest = 0

/** Loads the pins of the date on screen; an answer for a date the reader has already left is dropped. */
watch(() => [props.eventId, props.effectiveDate, placeKeys.value] as const, async ([eventId, date, keys]) => {
  const request = ++pinRequest
  if (!keys) {
    pins.value = []
    return
  }
  const loaded = await places.eventPlaces(eventId, date).catch(() => [])
  if (request === pinRequest) pins.value = loaded
}, {immediate: true})

/** The place fields whose map the reader has opened; every map starts closed. */
const openMaps = ref(new Set<number>())

/** Opens or closes the map of one place field, leaving the others as they are. */
function toggleMap(fieldId: number) {
  const next = new Set(openMaps.value)
  if (!next.delete(fieldId)) next.add(fieldId)
  openMaps.value = next
}

/** The pin of a place field as a list of at most one, empty where its text has none or it is no place field. */
function pinsOf(field: AppointmentField): StationPlace[] {
  if (field.fieldType !== FieldType.LOCATION) return []
  const key = normalisePlace(field.value)
  return pins.value.filter(pin => normalisePlace(pin.text) === key).slice(0, 1)
}
</script>

<template>
  <div class="grid grid-cols-12 gap-4">
    <div v-if="canEditEvent" class="col-span-12">
      <DetailLabel>{{ t('events.template') }}</DetailLabel>
      <p class="text-sm">{{ templateName }}</p>
    </div>
    <div v-for="field in fields" :key="field.id" :class="spanForWidth(configOf(field.config).width)">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <div class="flex items-start gap-1">
        <p class="text-sm">
          <QuestionValueDisplay :field-type="field.fieldType" :member-names="memberNames" :value="field.value"/>
        </p>
        <IconButton
            v-if="pinsOf(field).length > 0"
            :icon="['fas', 'map-location-dot']"
            :label="openMaps.has(field.id) ? t('places.hideMap') : t('places.showMap')"
            :aria-expanded="openMaps.has(field.id)"
            :aria-controls="`event-meta-place-map-${field.id}`"
            class="shrink-0 -my-2 text-primary"
            data-testid="event-meta-place-toggle"
            @click="toggleMap(field.id)"
        />
      </div>
      <div v-if="openMaps.has(field.id)" :id="`event-meta-place-map-${field.id}`">
        <PlaceMap
            v-for="pin in pinsOf(field)"
            :key="pin.text"
            :latitude="pin.latitude"
            :longitude="pin.longitude"
            :label="field.value"
            class="mt-2"
        />
      </div>
      <SelfRegistrationToggle
          :field="field"
          :current-member-id="currentMemberId"
          :toggle="() => events.toggleFieldSelfRegistration(eventId, field.id, effectiveDate)"
          :enter-label="field.readerInWholeGroup ? t('eventGroups.backOnThisDate') : undefined"
          :leave-label="field.readerInWholeGroup ? t('eventGroups.skipThisDate') : undefined"
          @updated="updated => emit('field-updated', updated)"
      />
      <PerDateFieldValue
          v-if="canEditEvent && effectiveDate && field.config.perDate"
          :event-id="eventId"
          :field="field"
          :date="effectiveDate"
          :all-members="allMembers"
          @saved="emit('field-updated', $event)"
      />
    </div>
  </div>
</template>
