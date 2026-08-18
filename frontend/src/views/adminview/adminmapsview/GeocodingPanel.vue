/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {MapsGeocodingConfig} from '@/api/maps'

const {t} = useI18n()

const geocoding = defineModel<MapsGeocodingConfig>({required: true})
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminMaps.geocodingSection') }}</SubHeader>
    <div class="space-y-1">
      <FieldLabel>{{ t('adminMaps.geocodingProvider') }}</FieldLabel>
      <SelectInput v-model="geocoding.provider">
        <option value="NONE">- None</option>
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
</template>
