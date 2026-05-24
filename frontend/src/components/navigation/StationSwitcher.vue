/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useStations} from '@/composables/useStations'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {
  stationList,
  loaded,
  load,
  currentStationId,
  setActiveStation,
  hasMultipleStations,
  activeStation,
  activeLogoUrl,
  getStationLogoUrl
} = useStations()
const {load: loadSession} = useSession()

const showModal = ref(false)

onMounted(() => {
  load()
})

async function switchStation(stationId: string) {
  setActiveStation(stationId)
  showModal.value = false
  await loadSession()
  router.push({name: 'dashboard-overview'})
}
</script>

<template>
  <template v-if="loaded && hasMultipleStations">
    <button
        class="flex items-center gap-2 text-sm text-(--text-muted) hover:text-(--text) transition-colors"
        @click="showModal = true"
    >
      <img v-if="activeLogoUrl" :src="activeLogoUrl" alt="" class="h-5 w-5 rounded object-contain"/>
      <font-awesome-icon v-else :icon="['fas', 'building']" class="h-3.5 w-3.5"/>
      <span>{{ activeStation?.stationName ?? t('stationSwitcher.noStation') }}</span>
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3"/>
    </button>

    <Modal v-model="showModal">
      <div class="space-y-4">
        <SubHeader class="text-lg font-semibold">{{ t('stationSwitcher.title') }}</SubHeader>
        <div class="space-y-2">
          <NeutralContainer
              v-for="station in stationList"
              :key="station.stationId"
              :class="station.stationId === currentStationId ? 'border-primary' : ''"
              class="flex items-center justify-between cursor-pointer hover:border-primary transition-colors"
              @click="switchStation(station.stationId)"
          >
            <div class="flex items-center gap-3">
              <img v-if="getStationLogoUrl(station.stationId)" :src="getStationLogoUrl(station.stationId)!" alt=""
                   class="h-6 w-6 rounded object-contain"/>
              <font-awesome-icon v-else :icon="['fas', 'building']" class="h-4 w-4 text-(--text-muted)"/>
              <span class="font-medium">{{ station.stationName }}</span>
            </div>
            <font-awesome-icon
                v-if="station.stationId === currentStationId"
                :icon="['fas', 'check']"
                class="text-primary"
            />
          </NeutralContainer>
        </div>
      </div>
    </Modal>
  </template>
</template>
