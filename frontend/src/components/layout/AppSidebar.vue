/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import {emberLogo} from '@/composables/useEmberLogo'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'

defineProps<{
  open: boolean
  stationName?: string
  stationLogoUrl?: string | null
}>()

defineEmits<{
  close: []
}>()

const {t} = useI18n()
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
    <div class="flex items-center h-14 border-b border-bg-light dark:border-bg-dark shrink-0">
      <router-link
          to="/"
          class="flex items-center gap-3 flex-1 min-w-0 h-full px-4 no-underline hover:bg-(--bg-accent) transition-colors"
      >
        <LayeredEmberLogo v-if="!stationLogoUrl" :layers="sidebarLogo.layers"
                          :active-layers="sidebarLogo.activeLayers" :auto-blink="true" size="h-8 w-8 shrink-0"
                          :pixel-size="64"/>
        <img v-else :src="stationLogoUrl" alt="" class="h-8 w-8 rounded object-contain shrink-0"/>
        <div
            :class="collapsed ? 'lg:opacity-0 lg:w-0 lg:pointer-events-none' : 'opacity-100'"
            class="flex flex-col justify-center min-w-0 transition-opacity duration-200 whitespace-nowrap"
        >
          <PrideText :active="prideActive" :variant="prideVariant"
                     class="text-lg font-bold text-primary leading-tight">Ember
          </PrideText>
          <span v-if="stationName" class="text-xs text-(--text-muted) leading-tight truncate">{{ stationName }}</span>
        </div>
      </router-link>
      <button
          type="button"
          :title="collapsed ? t('sidebar.expand') : t('sidebar.collapse')"
          :aria-label="collapsed ? t('sidebar.expand') : t('sidebar.collapse')"
          class="hidden lg:flex items-center justify-center w-8 h-8 mr-2 rounded-theme text-(--text-muted) hover:text-(--text) hover:bg-(--bg-accent) transition-colors shrink-0"
          :class="collapsed ? 'lg:hidden' : ''"
          @click="toggle"
      >
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="h-3.5 w-3.5"/>
      </button>
    </div>

    <button
        v-if="collapsed"
        type="button"
        :title="t('sidebar.expand')"
        :aria-label="t('sidebar.expand')"
        class="hidden lg:flex items-center justify-center h-8 mx-2 mt-2 rounded-theme text-(--text-muted) hover:text-(--text) hover:bg-(--bg-accent) transition-colors shrink-0"
        @click="toggle"
    >
      <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3.5 w-3.5"/>
    </button>

    <nav class="flex flex-col gap-1 p-3 overflow-y-auto overflow-x-hidden flex-1">
      <slot/>
    </nav>
  </aside>
</template>
