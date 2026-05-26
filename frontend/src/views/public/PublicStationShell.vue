/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, provide, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import SidebarLayout from '@/components/layout/SidebarLayout.vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {PublicStationInfo} from '@/api/discovery'
import {getPublicStationInfo} from '@/api/discovery'

const {t} = useI18n()
const route = useRoute()

const stationUid = computed(() => route.params.stationUid as string)
const station = ref<PublicStationInfo | null>(null)
const loading = ref(true)
const error = ref('')

const logoUrl = computed(() =>
    station.value?.hasLogo ? `/api/v1/public/stations/${stationUid.value}/logo` : null)

const basePath = computed(() => `/public/station/${stationUid.value}`)

onMounted(async () => {
  try {
    station.value = await getPublicStationInfo(stationUid.value)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
})

provide('publicStation', station)
</script>

<template>
  <Spinner v-if="loading" size="lg" class="mt-16"/>
  <Alert v-else-if="error" variant="error" class="m-8">{{ error }}</Alert>

  <SidebarLayout
      v-else-if="station"
      :station-name="station.name"
      :station-logo-url="logoUrl"
  >
    <template #sidebar="{ close }">
      <SidebarGroup v-if="station.hasPublicCalendar" :icon="['fas', 'calendar-days']" :label="t('publicStation.calendar')" :prefix="basePath + '/calendar'">
        <SidebarLink :icon="['fas', 'calendar']" name="public-station-calendar" :to="basePath + '/calendar'" @navigate="close">
          {{ t('publicStation.upcomingEvents') }}
        </SidebarLink>
      </SidebarGroup>

      <SidebarGroup v-if="station.hasPublicKb" :icon="['fas', 'book-open']" :label="t('publicStation.knowledgeBase')" :prefix="basePath + '/knowledge'">
        <SidebarLink :icon="['fas', 'folder-open']" name="public-kb" :to="basePath + '/knowledge'" @navigate="close">
          {{ t('publicStation.knowledgeBase') }}
        </SidebarLink>
      </SidebarGroup>
    </template>

    <template #header>
      <router-link to="/discovery">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'compass']" class="h-4 w-4"/>
          <span class="hidden sm:inline ml-1">{{ t('publicStation.backToDiscovery') }}</span>
        </SecondaryButton>
      </router-link>
    </template>

    <RouterView/>
  </SidebarLayout>
</template>
