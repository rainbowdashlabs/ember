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
import {useBreakpoint} from '@/composables/useBreakpoint'
import {usePlaces} from '@/composables/usePlaces'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'

/**
 * The foot of the sidebar, which switches between the reader's stations, associations, their own account
 * and the instance administration.
 *
 * <p>It shows the place the reader is in and opens a menu upwards with every place and the number waiting
 * in each; in the collapsed rail it shows only the place's picture and the menu opens beside the rail. The
 * sidebar cuts off whatever reaches past its edge, so the menu is put in the page itself, placed next to
 * the button, and as wide as its longest name allows; a name too long for that wraps rather than being
 * cut off. Somebody with nowhere else to go than their own account sees nothing here.
 */
const props = defineProps<{
  /** The name of the place shown, when the layout knows it better than the place list does. */
  name?: string
}>()

const {t} = useI18n()
const {places, current, switchable, crossStation, refreshCounts, go} = usePlaces()
const {collapsed} = useSidebarCollapse()
const {isDesktop} = useBreakpoint()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const menu = ref<HTMLElement | null>(null)
/** Where the open menu stands in the window, as the style that puts it there. */
interface MenuPlacement {
  left: string
  bottom: string
}

const placement = ref<MenuPlacement>({left: '0px', bottom: '0px'})

const rail = computed(() => collapsed.value && isDesktop.value)
const shownName = computed(() => current.value?.label ?? props.name ?? t('placeSwitcher.choose'))

/** Stands the menu above the button, or beside it in the rail, measured from where the button is now. */
function place() {
  const button = root.value?.getBoundingClientRect()
  if (!button) return
  placement.value = rail.value
    ? {left: `${button.right + 8}px`, bottom: `${window.innerHeight - button.bottom}px`}
    : {left: `${button.left}px`, bottom: `${window.innerHeight - button.top + 8}px`}
}

function toggle() {
  open.value = !open.value
  if (!open.value) return
  place()
  void refreshCounts()
}

useEventListener('mousedown', (event: MouseEvent) => {
  const target = event.target as Node
  if (open.value && !root.value?.contains(target) && !menu.value?.contains(target)) open.value = false
})

useEventListener('resize', () => {
  if (open.value) place()
})

useEventListener('keydown', (event: KeyboardEvent) => {
  if (open.value && event.key === 'Escape') open.value = false
})

function goToAllStations() {
  window.location.href = '/cross-station'
}
</script>

<template>
  <div v-if="switchable" ref="root" class="relative">
    <BareButton data-testid="place-switcher" :aria-expanded="open" aria-haspopup="menu" :title="rail ? shownName : undefined"
                :class="rail ? 'justify-center px-2' : 'px-3'"
                class="flex w-full items-center gap-3 rounded-theme py-2 text-left text-sm font-medium text-(--text) transition-colors hover:bg-primary/5"
                @click="toggle">
      <img v-if="current?.logoUrl" :src="current.logoUrl" alt="" class="h-5 w-5 shrink-0 rounded object-contain"/>
      <font-awesome-icon v-else :icon="['fas', current?.icon ?? 'building']" class="w-4 shrink-0"/>
      <template v-if="!rail">
        <span class="flex-1 line-clamp-2 break-words">{{ shownName }}</span>
        <font-awesome-icon :icon="['fas', open ? 'chevron-down' : 'chevron-up']" class="h-2.5 w-2.5 shrink-0"/>
      </template>
    </BareButton>
    <Teleport to="body">
    <div v-if="open" ref="menu" role="menu" data-testid="place-menu" :style="placement"
         class="fixed z-50 w-max min-w-60 max-w-80 overflow-hidden rounded-theme border border-(--border) bg-(--bg) py-1 shadow-xl">
      <BareButton v-for="entry in places" :key="entry.key" role="menuitem" :data-place="entry.key"
                  :class="entry.current ? 'text-primary' : 'text-(--text)'"
                  class="flex w-full items-center gap-3 px-3 py-2 text-left text-sm transition-colors hover:bg-primary/5"
                  @click="go(entry)">
        <img v-if="entry.logoUrl" :src="entry.logoUrl" alt="" class="h-5 w-5 shrink-0 rounded object-contain"/>
        <font-awesome-icon v-else :icon="['fas', entry.icon]" class="w-4 shrink-0"/>
        <span class="flex-1 break-words">{{ entry.label }}</span>
        <CountBadge v-if="entry.badge > 0" :count="entry.badge"/>
        <font-awesome-icon v-if="entry.current" :icon="['fas', 'check']" class="h-3 w-3 shrink-0"/>
      </BareButton>
      <BareButton v-if="crossStation" role="menuitem"
                  class="flex w-full items-center gap-3 border-t border-(--border) px-3 py-2 text-left text-sm text-(--text) transition-colors hover:bg-primary/5"
                  @click="goToAllStations">
        <font-awesome-icon :icon="['fas', 'layer-group']" class="w-4 shrink-0"/>
        <span class="flex-1">{{ t('placeSwitcher.allStations') }}</span>
      </BareButton>
    </div>
    </Teleport>
  </div>
</template>
