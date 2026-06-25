/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 *
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

defineProps<{
  hasLocalCoords: boolean
}>()

const query = defineModel<string>('query', {required: true})
const radius = defineModel<number>('radius', {required: true})
const nearMeOnly = defineModel<boolean>('nearMeOnly', {required: true})
const tab = defineModel<'list' | 'map'>('tab', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
      <div class="md:col-span-2">
        <SearchInput v-model="query" :placeholder="t('stationDiscovery.searchPlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationDiscovery.radiusLabel') }}</FieldLabel>
        <NumberInput v-model="radius" :min="1" :max="2000" :disabled="!hasLocalCoords"/>
      </div>
    </div>

    <div class="flex items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <ToggleInput v-model="nearMeOnly" :disabled="!hasLocalCoords"/>
        <span class="text-sm">{{ t('stationDiscovery.nearMeOnly') }}</span>
      </div>
      <div class="flex gap-1">
        <SelectionToggleButton :selected="tab === 'list'" @toggle="tab = 'list'">
          <font-awesome-icon :icon="['fas', 'list']" class="mr-1"/>
          {{ t('stationDiscovery.listTab') }}
        </SelectionToggleButton>
        <SelectionToggleButton :selected="tab === 'map'" @toggle="tab = 'map'">
          <font-awesome-icon :icon="['fas', 'map-location-dot']" class="mr-1"/>
          {{ t('stationDiscovery.mapTab') }}
        </SelectionToggleButton>
      </div>
    </div>

    <Alert v-if="!hasLocalCoords" variant="info">
      {{ t('stationDiscovery.noCoordinatesForFilter') }}
    </Alert>
  </NeutralContainer>
</template>
