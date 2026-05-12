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
}>()

const route = useRoute()
const isActive = computed(() => (route.path + '/').startsWith(props.prefix + '/') || route.path === props.prefix)
const expanded = ref(isActive.value)

watch(isActive, (active) => {
  if (active) expanded.value = true
})

function toggle() {
  expanded.value = !expanded.value
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
