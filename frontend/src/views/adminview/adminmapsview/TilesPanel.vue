/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import TilesProviderFields from './TilesProviderFields.vue'
import {maps} from '@/api'
import type {MapsTilesConfig} from '@/api/maps'
import type {MapTileProvider} from '@/composables/useMapsConfig'

const {t} = useI18n()

const tiles = defineModel<MapsTilesConfig>({required: true})

interface GeocodingTestResult {
  ok: boolean
  status: number
}

const testResult = ref<GeocodingTestResult | null>(null)

const providersRequiringKey: MapTileProvider[] = ['MAPBOX', 'STADIA', 'MAPTILER', 'THUNDERFOREST']
const requiresKey = computed(() => providersRequiringKey.includes(tiles.value.provider))
const isCustom = computed(() => tiles.value.provider === 'CUSTOM')

async function runTestTile() {
  try {
    const result = await maps.testTile()
    const ok = result.status >= 200 && result.status < 300
    testResult.value = {ok, status: result.status}
  } catch {
    testResult.value = {ok: false, status: -1}
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminMaps.tilesSection') }}</SubHeader>

    <TilesProviderFields v-model="tiles" :requires-key="requiresKey" :is-custom="isCustom"/>

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
</template>
