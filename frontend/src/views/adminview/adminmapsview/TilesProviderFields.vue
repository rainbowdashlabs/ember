/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {MapsTilesConfig} from '@/api/maps'

const {t} = useI18n()

defineProps<{
  requiresKey: boolean
  isCustom: boolean
}>()

const tiles = defineModel<MapsTilesConfig>({required: true})
const showApiKey = ref(false)
</script>

<template>
  <div class="space-y-3">
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
  </div>
</template>
