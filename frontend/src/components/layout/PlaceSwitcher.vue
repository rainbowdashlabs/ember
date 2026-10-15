/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useEventListener} from '@vueuse/core'
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import CountBadge from '@/components/badge/CountBadge.vue'
import {usePlaces} from '@/composables/usePlaces'

/**
 * The name at the top of the sidebar, which switches between the reader's stations, associations and
 * the instance administration.
 *
 * <p>Somebody with one station and nothing else has nowhere to go, so for them the name stays plain
 * text. Everybody else gets a chevron and a menu right under the name, each place with the number
 * waiting there.
 */
const props = defineProps<{
  /** The name of the place shown, when the layout knows it better than the place list does. */
  name?: string
}>()

const {t} = useI18n()
const {places, current, switchable, crossStation, refreshCounts, go} = usePlaces()

const open = ref(false)
const root = ref<HTMLElement | null>(null)

const shownName = computed(() => props.name ?? current.value?.label ?? t('placeSwitcher.choose'))

function toggle() {
  open.value = !open.value
  if (open.value) void refreshCounts()
}

useEventListener('mousedown', (event: MouseEvent) => {
  if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
})

useEventListener('keydown', (event: KeyboardEvent) => {
  if (open.value && event.key === 'Escape') open.value = false
})

function goToAllStations() {
  window.location.href = '/cross-station'
}
</script>

<template>
  <span v-if="!switchable" class="text-xs text-(--text-muted) leading-tight line-clamp-2">{{ props.name }}</span>
  <div v-else ref="root" class="relative">
    <BareButton data-testid="place-switcher" :aria-expanded="open" aria-haspopup="menu"
                class="flex max-w-full items-center gap-1 text-xs leading-tight text-(--text-muted) transition-colors hover:text-(--text)"
                @click="toggle">
      <span class="truncate">{{ shownName }}</span>
      <font-awesome-icon :icon="['fas', open ? 'chevron-up' : 'chevron-down']" class="h-2.5 w-2.5 shrink-0"/>
    </BareButton>
    <div v-if="open" role="menu"
         class="absolute left-0 top-full z-50 mt-2 w-60 overflow-hidden rounded-theme border border-(--border) bg-(--bg) py-1 shadow-xl">
      <BareButton v-for="place in places" :key="place.key" role="menuitem" :data-place="place.key"
                  :class="place.current ? 'text-primary' : 'text-(--text)'"
                  class="flex w-full items-center gap-3 px-3 py-2 text-left text-sm transition-colors hover:bg-primary/5"
                  @click="go(place)">
        <img v-if="place.logoUrl" :src="place.logoUrl" alt="" class="h-5 w-5 shrink-0 rounded object-contain"/>
        <font-awesome-icon v-else :icon="['fas', place.icon]" class="w-4 shrink-0"/>
        <span class="flex-1 truncate">{{ place.label }}</span>
        <CountBadge v-if="place.badge > 0" :count="place.badge"/>
        <font-awesome-icon v-if="place.current" :icon="['fas', 'check']" class="h-3 w-3 shrink-0"/>
      </BareButton>
      <BareButton v-if="crossStation" role="menuitem"
                  class="flex w-full items-center gap-3 border-t border-(--border) px-3 py-2 text-left text-sm text-(--text) transition-colors hover:bg-primary/5"
                  @click="goToAllStations">
        <font-awesome-icon :icon="['fas', 'layer-group']" class="w-4 shrink-0"/>
        <span class="flex-1 truncate">{{ t('placeSwitcher.allStations') }}</span>
      </BareButton>
    </div>
  </div>
</template>
