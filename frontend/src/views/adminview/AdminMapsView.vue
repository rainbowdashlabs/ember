/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import TilesPanel from './adminmapsview/TilesPanel.vue'
import PreviewPanel from './adminmapsview/PreviewPanel.vue'
import GeocodingPanel from './adminmapsview/GeocodingPanel.vue'
import CachePanel from './adminmapsview/CachePanel.vue'
import {maps} from '@/api'
import {useMapsConfig} from '@/composables/useMapsConfig'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {apiErrorMessage} from '@/util/apiError'
import type {
  AdminMapsConfig,
  MapsGeocodingConfig,
  MapsTilesConfig,
} from '@/api/maps'

const {t} = useI18n()
const {reload: reloadMapsConfig} = useMapsConfig()

const {message: flash, flash: showFlash} = useFlashMessage()

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
const showPurgeModal = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [config, stats] = await Promise.all([
    maps.getAdminMapsConfig(),
    maps.getCacheStats().catch(() => null),
  ])
  tiles.value = config.tiles
  geocoding.value = config.geocoding
  tileCacheMaxMb.value = config.tileCacheMaxMb
  cacheStats.value = stats
})

async function save() {
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
    await reloadMapsConfig()
  } catch (err) {
    error.value = apiErrorMessage(err) || t('common.error')
    throw err
  }
}

async function purgeCache() {
  try {
    const stats = await maps.purgeCache()
    cacheStats.value = stats
    showFlash(t('adminMaps.cachePurged'))
  } catch {
    error.value = t('common.error')
  } finally {
    showPurgeModal.value = false
  }
}
</script>

<template>
  <ViewContent :title="t('pages.admin-maps.title')" :subtitle="t('pages.admin-maps.subtitle')">
    <div class="space-y-6">
      <div>
        <p class="text-sm text-(--text-muted)">{{ t('adminMaps.subtitle') }}</p>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="flash" variant="success">{{ flash }}</Alert>

      <template v-if="!loading">
        <TilesPanel v-model="tiles"/>
        <PreviewPanel/>
        <GeocodingPanel v-model="geocoding"/>
        <CachePanel
            v-model="tileCacheMaxMb"
            :cache-stats="cacheStats"
            @request-purge="showPurgeModal = true"
        />

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
