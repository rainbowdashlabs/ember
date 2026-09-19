/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'

/**
 * The frame of every page inside the application: sidebar, header, the page, the footer.
 *
 * <p>The frame answers to `app-shell` in a test only once it runs in the browser. A page rendered on
 * the server looks finished before it is: its buttons are drawn but do nothing until the browser has
 * taken the page over, and a press in between is lost without a trace. Every end-to-end story waits
 * for the shell before it presses anything, so the shell appears when pressing works.
 */
withDefaults(defineProps<{
  title?: string
  subtitle?: string
  stationName?: string
  stationLogoUrl?: string | null
  collapsible?: boolean
}>(), {
  collapsible: true,
})

const sidebarOpen = ref(false)

const interactive = ref(false)
onMounted(() => { interactive.value = true })
</script>

<template>
  <div :data-testid="interactive ? 'app-shell' : undefined" class="flex min-h-screen">
    <AppSidebar :open="sidebarOpen" :station-logo-url="stationLogoUrl" :station-name="stationName"
                :collapsible="collapsible"
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
