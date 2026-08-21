/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {getItem} from '@/api/storage'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import SmartStationButton from '@/components/layout/SmartStationButton.vue'
import AdminPanelButton from '@/components/layout/AdminPanelButton.vue'
import ClusterPanelButton from '@/components/layout/ClusterPanelButton.vue'
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import {usePublicConfig} from '@/composables/usePublicConfig'
import { emberLogo } from '@/composables/useEmberLogo'

const {t} = useI18n()
const {sessionInfo, loaded, load} = useSession()
const {loaded: stationsLoaded, load: loadStations} = useStations()
const {prideActive, prideVariant} = usePride()
const logo = emberLogo()

const {isDemo} = await usePublicConfig()

/**
 * Whether this browser is carrying a session, which only the browser can answer.
 *
 * The server renders this header without any storage to read, so it knows neither that somebody is signed
 * in nor that nobody is. Rendering the login call to action on that ignorance put it in front of people
 * who were already signed in, and it stayed there until the session call came back. Undecided is its own
 * state and shows neither.
 */
const carriesSession = ref<boolean | null>(null)

const signedIn = computed(() => loaded.value && !!sessionInfo.value?.account)
const anonymous = computed(() =>
    carriesSession.value === false || (carriesSession.value === true && loaded.value && !sessionInfo.value?.account))

onMounted(() => {
  const token = getItem('session_token')
  carriesSession.value = !!token
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

    <div v-if="signedIn" class="flex items-center gap-3">
      <AdminPanelButton variant="primary"/>
      <ClusterPanelButton variant="primary"/>
      <SmartStationButton variant="primary"/>
      <AccountMenuButton/>
    </div>

    <router-link v-else-if="anonymous" to="/login">
      <PrimaryButton>
        {{ isDemo ? t('landing.tryNow') : t('header.login') }}
      </PrimaryButton>
    </router-link>
  </header>
</template>
