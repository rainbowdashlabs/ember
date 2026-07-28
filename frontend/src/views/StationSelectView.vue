/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {useStations} from '@/composables/useStations'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()
const {stationList, loaded, load, setActiveStation, getStationLogoUrl} = useStations()

function selectStation(stationId: string) {
  setActiveStation(stationId)
  const redirect = route.query.redirect as string | undefined
  window.location.href = redirect || '/station/dashboard/overview'
}

onMounted(async () => {
  if (!loaded.value) {
    await load()
  }
  const [onlyStation] = stationList.value
  if (stationList.value.length === 1 && onlyStation) {
    selectStation(onlyStation.stationId)
  }
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-md space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'building']"/>
        <SectionHeader>{{ t('stationSelect.title') }}</SectionHeader>
        <MutedText tag="p" size="sm" class="mt-1">{{ t('stationSelect.subtitle') }}</MutedText>
      </div>

      <Spinner v-if="!loaded" size="lg"/>

      <div v-if="loaded" class="space-y-3">
        <NeutralContainer
            v-for="station in stationList"
            :key="station.stationId"
            class="flex items-center justify-between cursor-pointer hover:border-primary transition-colors"
            @click="selectStation(station.stationId)"
        >
          <div class="flex items-center gap-2">
            <img v-if="getStationLogoUrl(station.stationId)" :src="getStationLogoUrl(station.stationId)!" alt=""
                 class="h-8 w-8 rounded object-contain"/>
            <font-awesome-icon v-else :icon="['fas', 'building']" class="h-5 w-5 text-(--text-muted)"/>
            <span class="font-medium text-lg">{{ station.stationName }}</span>
          </div>
          <font-awesome-icon :icon="['fas', 'chevron-right']" class="text-(--text-muted)"/>
        </NeutralContainer>
      </div>
    </div>
  </div>
</template>
