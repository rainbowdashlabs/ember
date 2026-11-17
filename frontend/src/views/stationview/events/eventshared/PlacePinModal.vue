/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LocationPicker from '@/components/map/LocationPicker.vue'
import GeocodeHint from '@/components/map/GeocodeHint.vue'
import type {GeocodePrecision, GeocodeResult} from '@/api/generated/schema'
import {places} from '@/api'
import {useGeocode} from '@/composables/useGeocode'
import {useMapsConfig} from '@/composables/useMapsConfig'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Where a place an appointment names lies on the map. It opens at the pin the station already keeps
 * for the place text, or looks the text up when there is none yet and the instance has an address
 * search; without one the pin is placed by hand. Saving stores the pin for the place text, so every
 * appointment naming the place shows it; the field's text itself never changes.
 */
const open = defineModel<boolean>({default: false})

const props = defineProps<{
  /** The place text, as the field holds it. */
  text: string
}>()

const {t} = useI18n()
const {load: loadMapsConfig} = useMapsConfig()

const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const found = ref<GeocodeResult | null>(null)
const preparing = ref(false)
const failure = ref<Failure | null>(null)

const {failure: findFailure, outcome, find, reset} = useGeocode(() => places.geocodePlace(props.text))

/** The precision of the lookup while the pin still sits where it put it; a moved pin is placed by hand. */
const precision = computed<GeocodePrecision | null>(() => {
  const match = found.value
  if (!match || latitude.value == null || longitude.value == null) return null
  const unmoved = Math.abs(match.latitude - latitude.value) < 1e-6 && Math.abs(match.longitude - longitude.value) < 1e-6
  return unmoved ? match.precision : null
})

async function prepare() {
  failure.value = null
  found.value = null
  reset()
  latitude.value = null
  longitude.value = null
  preparing.value = true
  try {
    const stored = await places.findPlace(props.text)
    if (stored) {
      latitude.value = stored.latitude
      longitude.value = stored.longitude
      if (stored.precision) {
        found.value = {latitude: stored.latitude, longitude: stored.longitude, precision: stored.precision}
      }
      return
    }
    const config = await loadMapsConfig()
    if (!config.geocoding) return
    const match = await find()
    if (findFailure.value) failure.value = findFailure.value
    if (match) {
      found.value = match
      latitude.value = Math.round(match.latitude * 1_000_000) / 1_000_000
      longitude.value = Math.round(match.longitude * 1_000_000) / 1_000_000
    }
  } catch (err) {
    failure.value = describeFailure(err, t)
  } finally {
    preparing.value = false
  }
}

watch(open, (isOpen) => {
  if (isOpen) void prepare()
}, {immediate: true})

async function save() {
  if (latitude.value == null || longitude.value == null) return
  failure.value = null
  try {
    await places.savePlace({
      text: props.text,
      latitude: latitude.value,
      longitude: longitude.value,
      precision: precision.value,
    })
    open.value = false
  } catch (err) {
    failure.value = describeFailure(err, t)
    throw err
  }
}
</script>

<template>
  <Modal v-model="open" size="lg">
    <div class="space-y-4">
      <SectionHeader>{{ t('places.title', {place: text}) }}</SectionHeader>
      <MutedText tag="p" size="sm">{{ t('places.hint') }}</MutedText>
      <FailureAlert :failure="failure"/>
      <Spinner v-if="preparing" size="md"/>
      <template v-else>
        <LocationPicker
            v-model:latitude="latitude"
            v-model:longitude="longitude"
            height="280px"
        />
        <GeocodeHint :outcome="outcome" target="place"/>
      </template>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="latitude == null || longitude == null" :action="save" data-testid="place-save"/>
      </ButtonRow>
    </div>
  </Modal>
</template>
