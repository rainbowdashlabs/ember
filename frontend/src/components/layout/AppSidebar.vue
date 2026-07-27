/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, provide, toRef} from 'vue'
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import {emberLogo} from '@/composables/useEmberLogo'
import {SIDEBAR_COLLAPSIBLE, useSidebarCollapse} from '@/composables/useSidebarCollapse'
import SidebarCollapseToggle from '@/components/layout/SidebarCollapseToggle.vue'

const props = withDefaults(defineProps<{
  open: boolean
  stationName?: string
  stationLogoUrl?: string | null
  collapsible?: boolean
}>(), {
  collapsible: true,
})

defineEmits<{
  close: []
}>()

provide(SIDEBAR_COLLAPSIBLE, toRef(props, 'collapsible'))

const {prideActive, prideVariant} = usePride()
const sidebarLogo = emberLogo()
const {collapsed, toggle} = useSidebarCollapse()

const desktopWidthClass = computed(() => collapsed.value ? 'lg:w-16' : 'lg:w-64')
</script>

<template>
  <div
      v-if="open"
      class="fixed inset-0 z-30 bg-black/50 lg:hidden"
      @click="$emit('close')"
  />

  <aside
      :class="[open ? 'translate-x-0' : '-translate-x-full', desktopWidthClass]"
      class="fixed top-0 left-0 z-40 h-full w-64 shrink-0 flex flex-col bg-bg-light-accent dark:bg-bg-dark-accent border-r border-bg-light-accent dark:border-bg-dark transition-[transform,width] duration-300 ease-in-out lg:translate-x-0 lg:static lg:z-auto lg:sticky lg:top-0 lg:h-screen overflow-hidden"
  >
    <div class="flex items-stretch min-h-14 border-b border-bg-light dark:border-bg-dark shrink-0">
      <router-link
          to="/"
          :class="collapsed ? 'lg:justify-center lg:px-2' : ''"
          class="flex items-center gap-3 flex-1 min-w-0 px-4 py-2 no-underline hover:bg-(--bg-accent) transition-colors"
      >
        <LayeredEmberLogo v-if="!stationLogoUrl" :layers="sidebarLogo.layers"
                          :active-layers="sidebarLogo.activeLayers" :auto-blink="true" size="h-8 w-8 shrink-0"
                          :pixel-size="64"/>
        <img v-else :src="stationLogoUrl" alt="" class="h-8 w-8 rounded object-contain shrink-0"/>
        <div
            :class="collapsed ? 'lg:hidden' : ''"
            class="flex flex-col justify-center min-w-0"
        >
          <PrideText :active="prideActive" :variant="prideVariant"
                     class="text-lg font-bold text-primary leading-tight whitespace-nowrap">Ember
          </PrideText>
          <span v-if="stationName" class="text-xs text-(--text-muted) leading-tight line-clamp-2">{{ stationName }}</span>
        </div>
      </router-link>
      <SidebarCollapseToggle
          v-if="collapsible"
          :collapsed="collapsed"
          class="hidden lg:inline-flex w-8 h-8 mr-2 self-center"
          :class="collapsed ? 'lg:hidden' : ''"
          @click="toggle"
      />
    </div>

    <SidebarCollapseToggle
        v-if="collapsible && collapsed"
        :collapsed="collapsed"
        class="hidden lg:inline-flex h-8 mx-2 mt-2"
        @click="toggle"
    />

    <nav class="flex flex-col gap-1 p-3 overflow-y-auto overflow-x-hidden flex-1">
      <slot/>
    </nav>
  </aside>
</template>
