/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import AdminPanelButton from '@/components/layout/AdminPanelButton.vue'
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import { emberLogo } from '@/composables/useEmberLogo'

const {t} = useI18n()
const {sessionInfo, loaded, load} = useSession()
const {loaded: stationsLoaded, load: loadStations} = useStations()
const {prideActive, prideVariant} = usePride()
const logo = emberLogo()

const {data: publicConfig} = await useAsyncData(
    'home-public-config',
    () => $fetch<{demoUrl?: string; demo?: boolean}>('/api/v1/public/config').catch(() => ({} as {demoUrl?: string; demo?: boolean})),
    {default: () => ({demoUrl: '', demo: false})},
)
const isDemo = computed(() => publicConfig.value?.demo ?? false)

onMounted(() => {
  const token = getItem('session_token')
  if (token) {
    if (!loaded.value) load()
    if (!stationsLoaded.value) loadStations()
  }
})
</script>

<template>
  <header
      class="flex h-14 items-center justify-between border-b border-bg-light-accent dark:border-bg-dark-accent px-4">
    <router-link class="flex items-center gap-2 text-lg font-bold text-primary no-underline hover:no-underline" to="/">
      <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" size="h-7 w-7" :pixel-size="64" />
      <PrideText :active="prideActive" :variant="prideVariant">Ember</PrideText>
    </router-link>

    <div v-if="loaded && sessionInfo?.account" class="flex items-center gap-3">
      <AdminPanelButton variant="primary"/>
      <SmartStationButton variant="primary"/>
      <AccountMenuButton/>
    </div>

    <router-link v-else to="/login">
      <PrimaryButton>
        {{ isDemo ? t('landing.tryNow') : t('header.login') }}
      </PrimaryButton>
    </router-link>
  </header>
</template>
