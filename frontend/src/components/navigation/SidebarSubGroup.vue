/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useSlots, watch} from 'vue'
import {useRoute} from 'vue-router'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'
import {useSidebarFlyoutParent} from '@/composables/useSidebarFlyoutContext'
import {useFlyoutHover} from '@/composables/useFlyoutHover'
import SidebarFlyoutMenu from '@/components/navigation/SidebarFlyoutMenu.vue'
import {collectSidebarPaths} from '@/util/sidebarEntries'

const {collapsed} = useSidebarCollapse()
const parentFlyout = useSidebarFlyoutParent()
const inFlyout = parentFlyout !== null

const props = defineProps<{
  label: string
  /** Pages this subsection covers that are not entries in it. What its entries lead to is read off them. */
  prefix?: string | string[]
  icon?: string[]
  badge?: number
}>()

const route = useRoute()
const slots = useSlots()

const prefixes = computed(() => {
  const written = Array.isArray(props.prefix) ? props.prefix : props.prefix ? [props.prefix] : []
  const slotFn = slots.default
  return [...written, ...(slotFn ? collectSidebarPaths(slotFn()) : [])]
})

const isActive = computed(() => prefixes.value.some(
    prefix => (route.path + '/').startsWith(prefix + '/') || route.path === prefix))
const expanded = ref(isActive.value)

watch(isActive, (active) => {
  if (active) expanded.value = true
})

const hiddenWhenCollapsed = computed(() => collapsed.value && !inFlyout)

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

function onRowClick() {
  if (inFlyout) {
    flyoutForce(!flyoutOpen.value)
    return
  }
  expanded.value = !expanded.value
}

function onRowEnter() {
  if (inFlyout) flyoutEnter()
}

function onRowLeave() {
  if (inFlyout) flyoutLeave()
}

function onPanelEnter() {
  parentFlyout?.enter()
  flyoutEnter()
}

function onPanelLeave() {
  parentFlyout?.leave()
  flyoutLeave()
}

function onChildNavigate() {
  flyoutForce(false)
  parentFlyout?.close()
}
</script>

<template>
  <div :class="hiddenWhenCollapsed ? 'lg:hidden' : ''">
    <button
        type="button"
        :ref="setAnchor"
        :class="isActive
        ? 'text-primary'
        : 'text-[var(--text-muted)] hover:text-[var(--text)]'"
        class="flex w-full items-center gap-2 rounded-theme px-2 py-1.5 text-xs font-medium transition-colors duration-150"
        @click="onRowClick"
        @mouseenter="onRowEnter"
        @mouseleave="onRowLeave"
        @focusin="onRowEnter"
        @focusout="onRowLeave"
    >
      <font-awesome-icon v-if="icon" :icon="icon" class="h-3 w-3"/>
      <span class="flex-1 text-left truncate">{{ label }}</span>
      <span v-if="badge && badge > 0"
            class="inline-flex items-center justify-center min-w-4 h-4 px-1 rounded-full text-[10px] font-bold bg-error text-error-text">{{
          badge
        }}</span>
      <font-awesome-icon
          v-if="!inFlyout"
          :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
          class="h-2.5 w-2.5"
      />
      <font-awesome-icon
          v-else
          :icon="['fas', 'chevron-right']"
          class="h-2.5 w-2.5"
      />
    </button>

    <div v-if="!inFlyout && expanded" class="ml-3 flex flex-col gap-0.5 mt-0.5">
      <slot/>
    </div>

    <SidebarFlyoutMenu
        v-if="inFlyout"
        :anchor="anchorEl"
        :open="flyoutOpen"
        :label="label"
        :icon="icon"
        :badge="badge"
        @enter="onPanelEnter"
        @leave="onPanelLeave"
        @close="flyoutForce(false)"
    >
      <div @click="onChildNavigate">
        <slot/>
      </div>
    </SidebarFlyoutMenu>
  </div>
</template>
