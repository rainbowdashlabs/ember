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
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {GeocodingProvider, type MapsGeocodingConfig} from '@/api/generated/schema'

/**
 * Who looks up addresses for this instance: nobody, or a Photon server. Without a server address
 * the public one is asked; an operator running their own names it here.
 */
const {t} = useI18n()

const geocoding = defineModel<MapsGeocodingConfig>({required: true})

defineProps<{
  /** The Photon server asked while no address is entered, as the server names it. */
  defaultServer: string | null
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminMaps.geocodingSection') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('adminMaps.geocodingHelp') }}</MutedText>
    <div class="space-y-1">
      <FieldLabel>{{ t('adminMaps.geocodingProvider') }}</FieldLabel>
      <SelectInput v-model="geocoding.provider" data-testid="geocoding-provider">
        <option :value="GeocodingProvider.NONE">{{ t('adminMaps.geocodingNone') }}</option>
        <option :value="GeocodingProvider.PHOTON">{{ t('adminMaps.geocodingPhoton') }}</option>
      </SelectInput>
    </div>
    <div v-if="geocoding.provider === GeocodingProvider.PHOTON" class="space-y-1">
      <FieldLabel>{{ t('adminMaps.geocodingServer') }}</FieldLabel>
      <TextInput
          :model-value="geocoding.baseUrl ?? ''"
          :placeholder="defaultServer ?? undefined"
          data-testid="geocoding-server"
          @update:model-value="value => geocoding.baseUrl = value ?? ''"
      />
      <MutedText tag="p" size="sm">{{ t('adminMaps.geocodingServerHint') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
