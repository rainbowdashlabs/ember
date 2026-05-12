/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'

defineProps<{
  title?: string
  subtitle?: string
  stationName?: string
  stationLogoUrl?: string | null
}>()

const sidebarOpen = ref(false)
</script>

<template>
  <div class="flex min-h-screen">
    <AppSidebar :open="sidebarOpen" :station-logo-url="stationLogoUrl" :station-name="stationName"
                @close="sidebarOpen = false">
      <slot :close="() => sidebarOpen = false" name="sidebar"/>
    </AppSidebar>

    <div class="flex flex-1 flex-col min-w-0">
      <AppHeader :subtitle="subtitle" :title="title" @toggle-sidebar="sidebarOpen = !sidebarOpen">
        <slot name="header"/>
      </AppHeader>

      <main class="flex-1">
        <slot/>
      </main>

      <AppFooter>
        <slot name="footer"/>
      </AppFooter>
    </div>
  </div>
</template>
