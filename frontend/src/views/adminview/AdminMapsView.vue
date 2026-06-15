/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import StationMap from '@/components/map/StationMap.vue'
import {maps} from '@/api'
import {useMapsConfig} from '@/composables/useMapsConfig'
import type {
  AdminMapsConfig,
  GeocodingProvider,
  MapsGeocodingConfig,
  MapsTilesConfig,
} from '@/api/maps'
import type {MapTileProvider} from '@/composables/useMapsConfig'

const {t} = useI18n()
const {reload} = useMapsConfig()

const loading = ref(true)
const error = ref('')
const flash = ref('')

const tiles = ref<MapsTilesConfig>({
  provider: 'OSM',
  apiKey: '',
  urlTemplate: '',
  attribution: '',
  minZoom: 0,
  maxZoom: 19,
})
const geocoding = ref<MapsGeocodingConfig>({
  provider: 'NONE',
  apiKey: '',
  contactEmail: '',
})
const tileCacheMaxMb = ref(500)
const cacheStats = ref<maps.TileCacheStats | null>(null)
const testResult = ref<{ok: boolean; status: number} | null>(null)
const showApiKey = ref(false)
const showPurgeModal = ref(false)

const previewStations = computed(() => [
  {uid: 'preview', name: 'Preview', latitude: 51.0, longitude: 10.0},
])

const providersRequiringKey: MapTileProvider[] = ['MAPBOX', 'STADIA', 'MAPTILER', 'THUNDERFOREST']
const requiresKey = computed(() => providersRequiringKey.includes(tiles.value.provider))
const isCustom = computed(() => tiles.value.provider === 'CUSTOM')

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    const [config, stats] = await Promise.all([
      maps.getAdminMapsConfig(),
      maps.getCacheStats().catch(() => null),
    ])
    tiles.value = config.tiles
    geocoding.value = config.geocoding
    tileCacheMaxMb.value = config.tileCacheMaxMb
    cacheStats.value = stats
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function save() {
  flash.value = ''
  error.value = ''
  try {
    const payload: AdminMapsConfig = {
      tiles: tiles.value,
      geocoding: geocoding.value,
      tileCacheMaxMb: tileCacheMaxMb.value,
    }
    const saved = await maps.updateAdminMapsConfig(payload)
    tiles.value = saved.tiles
    geocoding.value = saved.geocoding
    tileCacheMaxMb.value = saved.tileCacheMaxMb
    await reload()
  } catch (err: any) {
    error.value = err?.response?.data?.title || t('common.error')
    throw err
  }
}

async function runTestTile() {
  try {
    const result = await maps.testTile()
    const ok = result.status >= 200 && result.status < 300
    testResult.value = {ok, status: result.status}
  } catch {
    testResult.value = {ok: false, status: -1}
  }
}

async function purgeCache() {
  try {
    const stats = await maps.purgeCache()
    cacheStats.value = stats
    flash.value = t('adminMaps.cachePurged')
  } catch {
    error.value = t('common.error')
  } finally {
    showPurgeModal.value = false
  }
}

function formatMb(bytes: number): string {
  return (bytes / (1024 * 1024)).toFixed(1)
}

onMounted(loadAll)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('adminMaps.title') }}</SectionHeader>
        <p class="text-sm text-(--text-muted)">{{ t('adminMaps.subtitle') }}</p>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="flash" variant="success">{{ flash }}</Alert>

      <template v-if="!loading">
        <!-- Tile provider -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminMaps.tilesSection') }}</SubHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminMaps.provider') }}</FieldLabel>
            <p class="text-xs text-(--text-muted)">{{ t('adminMaps.providerHelp') }}</p>
            <SelectInput v-model="tiles.provider">
              <option value="OSM">OpenStreetMap (Raw)</option>
              <option value="STADIA">Stadia Maps</option>
              <option value="MAPBOX">Mapbox</option>
              <option value="MAPTILER">MapTiler</option>
              <option value="THUNDERFOREST">Thunderforest</option>
              <option value="CUSTOM">{{ t('adminMaps.urlTemplate') }} (Custom)</option>
            </SelectInput>
          </div>

          <div v-if="requiresKey || isCustom" class="space-y-1">
            <FieldLabel>{{ t('adminMaps.apiKey') }}</FieldLabel>
            <div class="flex gap-2">
              <TextInput
                  v-model="tiles.apiKey"
                  :type="showApiKey ? 'text' : 'password'"
                  :placeholder="t('adminMaps.apiKeyPlaceholder')"
              />
              <SecondaryButton compact :icon="['fas', showApiKey ? 'eye-slash' : 'eye']"
                               @click="showApiKey = !showApiKey"/>
            </div>
          </div>

          <div v-if="isCustom" class="space-y-1">
            <FieldLabel>{{ t('adminMaps.urlTemplate') }}</FieldLabel>
            <TextInput v-model="tiles.urlTemplate" :placeholder="t('adminMaps.urlTemplatePlaceholder')"/>
          </div>

          <div class="space-y-1">
            <FieldLabel>{{ t('adminMaps.attribution') }}</FieldLabel>
            <TextInput v-model="tiles.attribution" :placeholder="t('adminMaps.attributionPlaceholder')"/>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <FieldLabel>{{ t('adminMaps.minZoom') }}</FieldLabel>
              <NumberInput v-model="tiles.minZoom" :min="0" :max="22"/>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('adminMaps.maxZoom') }}</FieldLabel>
              <NumberInput v-model="tiles.maxZoom" :min="0" :max="22"/>
            </div>
          </div>

          <div class="flex flex-wrap gap-2 pt-2">
            <SecondaryButton :icon="['fas', 'satellite-dish']" @click="runTestTile">
              {{ t('adminMaps.testTile') }}
            </SecondaryButton>
            <Alert v-if="testResult?.ok" variant="success">
              {{ t('adminMaps.testTileSuccess', {status: testResult.status}) }}
            </Alert>
            <Alert v-if="testResult && !testResult.ok" variant="error">
              {{ t('adminMaps.testTileFailure', {status: testResult.status}) }}
            </Alert>
          </div>
        </NeutralContainer>

        <!-- Live preview -->
        <NeutralContainer class="space-y-3">
          <SubHeader>Preview</SubHeader>
          <StationMap
              :stations="previewStations"
              :cluster="false"
              :fit-on-update="false"
              :initial-center="[51.0, 10.0]"
              :initial-zoom="6"
              height="280px"
          />
        </NeutralContainer>

        <!-- Geocoding -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminMaps.geocodingSection') }}</SubHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminMaps.geocodingProvider') }}</FieldLabel>
            <SelectInput v-model="geocoding.provider">
              <option value="NONE">— None</option>
              <option value="NOMINATIM">Nominatim</option>
              <option value="LOCATIONIQ">LocationIQ</option>
              <option value="GEOAPIFY">Geoapify</option>
            </SelectInput>
          </div>
          <div v-if="geocoding.provider !== 'NONE'" class="space-y-1">
            <FieldLabel>{{ t('adminMaps.apiKey') }}</FieldLabel>
            <TextInput v-model="geocoding.apiKey" type="password" :placeholder="t('adminMaps.apiKeyPlaceholder')"/>
          </div>
          <div v-if="geocoding.provider === 'NOMINATIM'" class="space-y-1">
            <FieldLabel>{{ t('adminMaps.contactEmail') }}</FieldLabel>
            <TextInput v-model="geocoding.contactEmail" placeholder="admin@example.com"/>
          </div>
        </NeutralContainer>

        <!-- Cache -->
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('adminMaps.cacheSection') }}</SubHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('adminMaps.cacheMaxMb') }}</FieldLabel>
            <NumberInput v-model="tileCacheMaxMb" :min="0" :max="10000"/>
          </div>
          <div v-if="cacheStats" class="space-y-1">
            <FieldLabel>{{ t('adminMaps.cacheStatsLabel') }}</FieldLabel>
            <p class="text-sm">
              {{
                t('adminMaps.cacheStatsValue', {
                  tiles: cacheStats.tiles,
                  used: formatMb(cacheStats.bytes),
                  max: formatMb(cacheStats.maxBytes),
                })
              }}
            </p>
          </div>
          <DeleteButton @click="showPurgeModal = true">
            {{ t('adminMaps.cachePurge') }}
          </DeleteButton>
        </NeutralContainer>

        <div class="flex justify-end">
          <SaveButton :action="save"/>
        </div>
      </template>
    </div>

    <Modal v-model="showPurgeModal" :title="t('adminMaps.cachePurge')">
      <p>{{ t('adminMaps.cachePurgeConfirm') }}</p>
      <template #footer>
        <SecondaryButton @click="showPurgeModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <DeleteButton @click="purgeCache">{{ t('adminMaps.cachePurge') }}</DeleteButton>
      </template>
    </Modal>
  </ViewContent>
</template>
