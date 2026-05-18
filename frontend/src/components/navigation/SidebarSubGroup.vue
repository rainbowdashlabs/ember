/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useRoute} from 'vue-router'

const props = defineProps<{
  label: string
  prefix: string
}>()

const route = useRoute()
const isActive = computed(() => (route.path + '/').startsWith(props.prefix + '/') || route.path === props.prefix)
const expanded = ref(isActive.value)

watch(isActive, (active) => {
  if (active) expanded.value = true
})
</script>

<template>
  <div>
    <button
        :class="isActive
        ? 'text-primary'
        : 'text-[var(--text-muted)] hover:text-[var(--text)]'"
        class="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-xs font-medium transition-colors duration-150"
        @click="expanded = !expanded"
    >
      <font-awesome-icon
          :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
          class="h-2.5 w-2.5"
      />
      <span class="flex-1 text-left">{{ label }}</span>
    </button>
    <div v-if="expanded" class="ml-3 flex flex-col gap-0.5 mt-0.5">
      <slot/>
    </div>
  </div>
</template>
