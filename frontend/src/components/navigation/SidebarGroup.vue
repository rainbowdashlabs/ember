/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useSlots, watch} from 'vue'
import {useRoute} from 'vue-router'
import type {VNode} from 'vue'

const props = defineProps<{
  icon?: string[]
  label: string
  prefix: string | string[]
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
const slots = useSlots()
const prefixes = computed(() => Array.isArray(props.prefix) ? props.prefix : [props.prefix])
const isActive = computed(() => prefixes.value.some(p => (route.path + '/').startsWith(p + '/') || route.path === p))

function countVisibleVNodes(vnodes: VNode[]): number {
  let count = 0
  for (const vnode of vnodes) {
    if (vnode.type === Comment) continue
    if (typeof vnode.type === 'symbol' && Array.isArray(vnode.children)) {
      // Fragment — recurse into children
      count += countVisibleVNodes(vnode.children as VNode[])
    } else {
      count++
    }
  }
  return count
}

const hasVisibleChildren = computed(() => {
  const slotFn = slots.default
  if (!slotFn) return false
  return countVisibleVNodes(slotFn()) > 0
})

const localExpanded = ref(isActive.value)
const key = computed(() => props.groupKey ?? (Array.isArray(props.prefix) ? props.prefix[0] : props.prefix))
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

</script>

<template>
  <div>
    <div class="flex items-center">
      <component
          :is="to ? 'router-link' : 'button'"
          :to="to"
          :class="isActive
          ? '!text-primary'
          : '!text-[var(--text)] hover:bg-primary/5'"
          class="flex flex-1 items-center gap-3 rounded-theme px-3 py-2 text-sm font-medium no-underline transition-colors duration-150"
          @click="to ? emit('navigate') : toggle()"
      >
        <font-awesome-icon v-if="icon" :icon="icon" class="w-4"/>
        <span class="flex-1 text-left">{{ label }}</span>
        <span v-if="badge && badge > 0"
              class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-error-text">{{
            badge
          }}</span>
      </component>
      <button
          v-if="hasVisibleChildren"
          class="flex items-center justify-center w-8 h-8 rounded-theme text-[var(--text)] transition-colors duration-150"
          @click="toggle"
      >
        <font-awesome-icon
            :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
            class="h-3 w-3"
        />
      </button>
    </div>

    <div v-if="hasVisibleChildren && expanded" class="ml-4 flex flex-col gap-1 mt-1">
      <slot/>
    </div>
  </div>
</template>
