/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {Comment as VComment, computed, ref, useSlots, watch, type VNode} from 'vue'
import {useRoute} from 'vue-router'
import {useSidebarCollapse} from '@/composables/useSidebarCollapse'

const {collapsed} = useSidebarCollapse()

const props = defineProps<{
  to: string
  name: string
  icon?: string[]
  prefix?: string | string[]
}>()

defineEmits<{
  navigate: []
}>()

const route = useRoute()
const isLinkActive = computed(() => route.name === props.name)
const prefixes = computed(() => {
  const p = props.prefix ?? props.to
  return Array.isArray(p) ? p : [p]
})
const isChildActive = computed(() => {
  if (isLinkActive.value) return false
  return prefixes.value.some(p => (route.path + '/').startsWith(p + '/') || route.path === p)
})
const isInPath = computed(() => isLinkActive.value || isChildActive.value)
const slots = useSlots()

function countVisibleVNodes(vnodes: VNode[]): number {
  let count = 0
  for (const vnode of vnodes) {
    if (vnode.type === VComment) continue
    if (typeof vnode.type === 'symbol' && Array.isArray(vnode.children)) {
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

const expanded = ref(isInPath.value)

watch(isInPath, (active) => {
  if (active) expanded.value = true
})
</script>

<template>
  <div>
    <div class="flex items-center">
      <router-link
          :class="[
            isInPath ? '!text-primary' : '!text-[var(--text)] hover:bg-primary/5',
            collapsed ? 'lg:justify-center lg:px-2 px-3' : 'px-3',
          ]"
          :to="to"
          class="flex flex-1 items-center gap-3 rounded-theme py-2 text-sm font-medium no-underline transition-colors duration-150"
          @click="$emit('navigate')"
      >
        <font-awesome-icon v-if="icon" :icon="icon" class="w-4 shrink-0"/>
        <span class="flex-1 truncate" :class="collapsed ? 'lg:hidden' : ''"><slot name="label"/></span>
      </router-link>
      <button
          v-if="hasVisibleChildren"
          class="flex items-center justify-center w-8 h-8 rounded-theme text-[var(--text)] transition-colors duration-150"
          :class="collapsed ? 'lg:hidden' : ''"
          @click="expanded = !expanded"
      >
        <font-awesome-icon
            :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
            class="h-2.5 w-2.5"
        />
      </button>
    </div>
    <div v-if="hasVisibleChildren && expanded" class="ml-3 flex flex-col gap-0.5 mt-0.5" :class="collapsed ? 'lg:hidden' : ''">
      <slot/>
    </div>
  </div>
</template>
