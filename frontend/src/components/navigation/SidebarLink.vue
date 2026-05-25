/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useRoute} from 'vue-router'

const route = useRoute()

const props = defineProps<{
  to: string
  name: string
  icon?: string[]
  badge?: number
}>()

defineEmits<{
  navigate: []
}>()
</script>

<template>
  <router-link
      :class="route.name === name
      ? 'bg-primary/15 !text-primary'
      : '!text-[var(--text)] hover:bg-primary/5'"
      :to="to"
      class="flex items-center gap-3 rounded-theme px-3 py-2 text-sm font-medium no-underline transition-colors duration-150"
      @click="$emit('navigate')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="w-4"/>
    <span class="flex-1"><slot/></span>
    <span v-if="badge && badge > 0"
          class="inline-flex items-center justify-center min-w-5 h-5 px-1.5 rounded-full text-xs font-bold bg-error text-white">{{
        badge
      }}</span>
  </router-link>
</template>
