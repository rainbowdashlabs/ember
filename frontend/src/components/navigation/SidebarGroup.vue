/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'

const props = defineProps<{
  icon?: string[]
  label: string
  prefix: string
  badge?: number
  groupKey?: string
  openGroup?: string | null
}>()

const emit = defineEmits<{
  (e: 'update:openGroup', key: string | null): void
}>()

const route = useRoute()
const isActive = computed(() => (route.path + '/').startsWith(props.prefix + '/') || route.path === props.prefix)

const localExpanded = ref(isActive.value)
const key = computed(() => props.groupKey ?? props.prefix)
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
    <button
        :class="isActive
        ? 'text-primary'
        : 'text-[var(--text-muted)] hover:bg-primary/5 hover:text-[var(--text)]'"
        class="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-150"
        @click="toggle"
    >
      <font-awesome-icon v-if="icon" :icon="icon" class="w-4"/>
      <span class="flex-1 text-left">{{ label }}</span>
      <span v-if="badge && badge > 0"
            class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-white">{{
          badge
        }}</span>
      <font-awesome-icon
          :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
          class="h-3 w-3"
      />
    </button>

    <div v-if="expanded" class="ml-4 flex flex-col gap-1 mt-1">
      <slot/>
    </div>
  </div>
</template>
