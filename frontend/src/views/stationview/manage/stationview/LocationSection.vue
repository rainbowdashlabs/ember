/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import LocationPicker from '@/components/map/LocationPicker.vue'
import GeolocateButton from '@/components/map/GeolocateButton.vue'
import {stationManage} from '@/api'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {apiErrorMessage} from '@/util/apiError'

const emit = defineEmits<{
  (e: 'error', message: string): void
  (e: 'success', message: string): void
}>()

const {t} = useI18n()

const addressLine = ref<string>('')
const postalCode = ref<string>('')
const city = ref<string>('')
const country = ref<string>('')
const latitude = ref<number | null>(null)
const longitude = ref<number | null>(null)
const localError = ref('')

const loading = ref(true)

const picker = ref<InstanceType<typeof LocationPicker> | null>(null)

const countryOptions: {value: string; label: string}[] = [
  {value: '', label: ''},
  {value: 'DE', label: 'Deutschland'},
  {value: 'AT', label: 'Österreich'},
  {value: 'CH', label: 'Schweiz'},
  {value: 'LI', label: 'Liechtenstein'},
  {value: 'LU', label: 'Luxemburg'},
  {value: 'BE', label: 'Belgien'},
  {value: 'NL', label: 'Niederlande'},
  {value: 'IT', label: 'Italien'},
  {value: 'FR', label: 'Frankreich'},
  {value: 'PL', label: 'Polen'},
  {value: 'DK', label: 'Dänemark'},
  {value: 'SE', label: 'Schweden'},
  {value: 'NO', label: 'Norwegen'},
]

async function load() {
  loading.value = true
  try {
    const data = await stationManage.getStationLocation()
    addressLine.value = data.addressLine ?? ''
    postalCode.value = data.postalCode ?? ''
    city.value = data.city ?? ''
    country.value = data.country ?? ''
    latitude.value = data.latitude ?? null
    longitude.value = data.longitude ?? null
  } catch {
    emit('error', t('common.error'))
  } finally {
    loading.value = false
  }
}

async function save() {
  localError.value = ''
  try {
    const saved = await stationManage.updateStationLocation({
      addressLine: addressLine.value || null,
      postalCode: postalCode.value || null,
      city: city.value || null,
      country: country.value || null,
      latitude: latitude.value,
      longitude: longitude.value,
    })
    addressLine.value = saved.addressLine ?? ''
    postalCode.value = saved.postalCode ?? ''
    city.value = saved.city ?? ''
    country.value = saved.country ?? ''
    latitude.value = saved.latitude ?? null
    longitude.value = saved.longitude ?? null
    emit('success', t('geolocation.saved'))
  } catch (err) {
    const msg = apiErrorMessage(err) || t('common.error')
    localError.value = msg
    emit('error', msg)
    throw err
  }
}

const {running: clearing, error: clearError, run: runClear} = useAsyncAction(async () => {
  await stationManage.clearStationLocation()
  addressLine.value = ''
  postalCode.value = ''
  city.value = ''
  country.value = ''
  latitude.value = null
  longitude.value = null
  emit('success', t('geolocation.cleared'))
})

async function clear() {
  await runClear()
  if (clearError.value) emit('error', clearError.value)
}

async function onLocated(lat: number, lng: number) {
  await picker.value?.setCoordinates(lat, lng)
}

function onGeolocateError(message: string) {
  localError.value = message
  emit('error', message)
}

onMounted(load)
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('geolocation.sectionTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('geolocation.sectionSubtitle') }}</p>

    <Spinner v-if="loading" size="md"/>
    <Alert v-if="localError" variant="error">{{ localError }}</Alert>

    <template v-if="!loading">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
        <div class="space-y-1 md:col-span-2">
          <FieldLabel>{{ t('geolocation.addressLine') }}</FieldLabel>
          <TextInput v-model="addressLine" :placeholder="t('geolocation.addressLinePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('geolocation.postalCode') }}</FieldLabel>
          <TextInput v-model="postalCode" :placeholder="t('geolocation.postalCodePlaceholder')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('geolocation.city') }}</FieldLabel>
          <TextInput v-model="city" :placeholder="t('geolocation.cityPlaceholder')"/>
        </div>
        <div class="space-y-1 md:col-span-2">
          <FieldLabel>{{ t('geolocation.country') }}</FieldLabel>
          <SelectInput v-model="country">
            <option v-for="opt in countryOptions" :key="opt.value" :value="opt.value">
              {{ opt.value ? `${opt.label} (${opt.value})` : t('geolocation.countryPlaceholder') }}
            </option>
          </SelectInput>
        </div>
      </div>

      <p class="text-sm text-(--text-muted)">{{ t('geolocation.pickHint') }}</p>

      <LocationPicker
          ref="picker"
          v-model:latitude="latitude"
          v-model:longitude="longitude"
      >
        <template #actions>
          <GeolocateButton @located="onLocated" @error="onGeolocateError"/>
        </template>
      </LocationPicker>

      <div class="flex flex-wrap gap-2 justify-end pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
        <DeleteButton :disabled="clearing" @click="clear">
          {{ t('geolocation.clear') }}
        </DeleteButton>
        <SaveButton :action="save"/>
      </div>
    </template>
  </NeutralContainer>
</template>
