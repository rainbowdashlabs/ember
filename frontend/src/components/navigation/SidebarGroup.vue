/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onUnmounted, ref, useSlots, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'
import {useSidebarInFlyout} from '@/composables/useSidebarFlyoutContext'
import {useFlyoutHover} from '@/composables/useFlyoutHover'
import SidebarFlyoutMenu from '@/components/navigation/SidebarFlyoutMenu.vue'
import {collectSidebarPaths, sidebarEntryVNodes} from '@/util/sidebarEntries'
import {
  bestSidebarMatch,
  claimSidebarGroup,
  releaseSidebarGroup,
  reportSidebarMatch,
} from '@/util/sidebarGroupState'

const props = defineProps<{
  icon?: string[]
  label: string
  /**
   * Pages this group covers that are not entries in it: a board's own page, a wizard step, anything
   * reached from a screen rather than from the sidebar. What its entries lead to is read off them.
   */
  prefix?: string | string[]
  to?: string
  name?: string
  badge?: number
  groupKey?: string
  openGroup?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', key: string | null): void
  (e: 'navigate'): void
}>()

const route = useRoute()
const router = useRouter()
const slots = useSlots()
/**
 * Every address this group covers: where its own entries lead, plus anything written on it by hand.
 *
 * <p>The written {@code prefix} is for the pages a group covers that are not entries in it, a board's own
 * page or a wizard step, and for a destination the markup works out at runtime, which is not a path
 * anything here can read.
 */
const prefixes = computed(() => {
  const written = Array.isArray(props.prefix) ? props.prefix : props.prefix ? [props.prefix] : []
  const slotFn = slots.default
  return [...written, ...(props.to ? [props.to] : []), ...(slotFn ? collectSidebarPaths(slotFn()) : [])]
      .filter(Boolean)
})

/** How well this group matches the page being shown: the length of its longest matching prefix. */
const matchLength = computed(() => {
  let best = 0
  for (const prefix of prefixes.value) {
    if ((route.path + '/').startsWith(prefix + '/') || route.path === prefix) {
      best = Math.max(best, prefix.length)
    }
  }
  return best
})

const groupId = claimSidebarGroup()
watch(matchLength, length => reportSidebarMatch(groupId, length), {immediate: true})
onUnmounted(() => releaseSidebarGroup(groupId))

/**
 * Lit only when nothing matches the page better. Without that the group declared `/cluster` would be
 * highlighted on every page of the association, which is the one group that says nothing about where
 * you are.
 *
 * <p>A best of zero means nobody has reported yet, which on a server render is every time, and matching
 * at all is then the best answer available.
 */
const isActive = computed(() => {
  if (matchLength.value === 0) return false
  const best = bestSidebarMatch.value
  return best === 0 || matchLength.value === best
})

const hasVisibleChildren = computed(() => {
  const slotFn = slots.default
  if (!slotFn) return false
  return sidebarEntryVNodes(slotFn()).length > 0
})

const localExpanded = ref(isActive.value)

/**
 * What the accordion calls this group. Written things only: a derived key would move when a permission
 * arrives and changes which entry comes first, and a group whose name changes under the accordion loses
 * whether it was open.
 */
const key = computed(() => {
  if (props.groupKey) return props.groupKey
  const written = Array.isArray(props.prefix) ? props.prefix[0] : props.prefix
  return written ?? props.to ?? props.label
})
const accordionMode = computed(() => props.openGroup !== undefined)

const expanded = computed(() => {
  if (accordionMode.value) {
    return props.openGroup === key.value
  }
  return localExpanded.value
})

watch(isActive, (active) => {
  if (active) {
    if (accordionMode.value) {
      emit('update:openGroup', key.value)
    } else {
      localExpanded.value = true
    }
  }
})

function toggle() {
  if (accordionMode.value) {
    emit('update:openGroup', expanded.value ? null : key.value)
  } else {
    localExpanded.value = !localExpanded.value
  }
}

const {collapsed} = useSidebarCollapse()
const inFlyout = useSidebarInFlyout()
const flyoutEnabled = computed(() => collapsed.value && hasVisibleChildren.value && !inFlyout)

const anchorEl = ref<HTMLElement | null>(null)

function setAnchor(value: unknown) {
  if (!value) {
    anchorEl.value = null
    return
  }
  if (value instanceof HTMLElement) {
    anchorEl.value = value
    return
  }
  const maybeComponent = value as { $el?: unknown }
  if (maybeComponent.$el instanceof HTMLElement) {
    anchorEl.value = maybeComponent.$el
  }
}

const {open: flyoutOpen, enter: flyoutEnter, leave: flyoutLeave, force: flyoutForce} = useFlyoutHover()

function onAnchorEnter() {
  if (flyoutEnabled.value) flyoutEnter()
}

function onAnchorLeave() {
  if (flyoutEnabled.value) flyoutLeave()
}

function onAnchorClick() {
  if (props.to) {
    flyoutForce(false)
    emit('navigate')
    return
  }
  if (flyoutEnabled.value) {
    flyoutForce(!flyoutOpen.value)
    return
  }
  toggle()
}

function onHeaderClick() {
  if (!props.to) return
  flyoutForce(false)
  emit('navigate')
  router.push(props.to)
}

function onChildNavigate() {
  flyoutForce(false)
  emit('navigate')
}

watch(() => collapsed.value, (value) => {
  if (!value) flyoutForce(false)
})
</script>

<template>
  <div>
    <div class="flex items-center">
      <component
          :is="to ? 'router-link' : 'button'"
          :to="to"
          :ref="setAnchor"
          :title="collapsed ? label : undefined"
          :data-active="isActive ? 'true' : undefined"
          :class="[
            isActive ? '!text-primary' : '!text-[var(--text)] hover:bg-primary/5',
            collapsed ? 'lg:justify-center lg:px-2 px-3' : 'px-3',
          ]"
          class="flex flex-1 items-center gap-3 rounded-theme py-2 text-sm font-medium no-underline transition-colors duration-150"
          @mouseenter="onAnchorEnter"
          @mouseleave="onAnchorLeave"
          @focusin="onAnchorEnter"
          @focusout="onAnchorLeave"
          @click="onAnchorClick"
      >
        <font-awesome-icon v-if="icon" :icon="icon" class="w-4 shrink-0"/>
        <span class="flex-1 text-left truncate" :class="collapsed ? 'lg:hidden' : ''">{{ label }}</span>
        <span v-if="badge && badge > 0"
              :class="collapsed ? 'lg:hidden' : ''"
              class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-error-text">{{
            badge
          }}</span>
      </component>
      <button
          v-if="hasVisibleChildren"
          class="flex items-center justify-center w-8 h-8 rounded-theme text-[var(--text)] transition-colors duration-150"
          :class="collapsed ? 'lg:hidden' : ''"
          @click="toggle"
      >
        <font-awesome-icon
            :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
            class="h-3 w-3"
        />
      </button>
    </div>

    <div v-if="hasVisibleChildren && expanded" class="ml-4 flex flex-col gap-1 mt-1" :class="collapsed ? 'lg:hidden' : ''">
      <slot/>
    </div>

    <SidebarFlyoutMenu
        v-if="flyoutEnabled"
        :anchor="anchorEl"
        :open="flyoutOpen"
        :label="label"
        :icon="icon"
        :badge="badge"
        :to="to"
        @enter="flyoutEnter"
        @leave="flyoutLeave"
        @close="flyoutForce(false)"
        @header-click="onHeaderClick"
    >
      <div @click="onChildNavigate">
        <slot/>
      </div>
    </SidebarFlyoutMenu>
  </div>
</template>
