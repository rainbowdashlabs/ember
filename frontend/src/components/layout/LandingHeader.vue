/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {useSignedIn} from '@/composables/useSignedIn'
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
const {signedIn, anonymous, carriesSession} = useSignedIn()
const {loaded: stationsLoaded, load: loadStations} = useStations()
const {prideActive, prideVariant} = usePride()
const logo = emberLogo()

const {isDemo} = await usePublicConfig()

watch(carriesSession, carries => {
  if (carries && !stationsLoaded.value) loadStations()
}, {immediate: true})
</script>

<template>
  <header
      class="flex h-14 items-center justify-between border-b border-bg-light-accent dark:border-bg-dark-accent px-4">
    <router-link class="flex items-center gap-2 text-lg font-bold text-primary no-underline hover:no-underline" to="/?home">
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
