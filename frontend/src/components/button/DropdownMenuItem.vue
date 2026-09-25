/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'

/**
 * One entry of a dropdown.
 *
 * <p>`destructive` is the entry that deletes something. It is coloured, and by convention it is
 * the last one in the menu: a full width row reads as harmless when it sits between two harmless
 * ones, which a red button in a toolbar never did.
 *
 * <p>`to` is the entry that opens a page rather than doing something, and such an entry is drawn as
 * the link it is, so it can be middle-clicked, copied and announced. An entry the reader may not
 * take stays the disabled button it was, because a link cannot be switched off.
 */
const props = defineProps<{
  icon?: string | string[]
  iconClass?: string
  disabled?: boolean
  destructive?: boolean
  to?: RouteLocationRaw | null
}>()

defineEmits<{
  click: []
}>()

const opensPage = computed(() => props.to != null && !props.disabled)

const entryClass = computed(() => [
  'w-full text-left px-4 py-2 text-sm hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent flex items-center gap-2 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed',
  props.destructive ? 'text-error' : '',
])

const glyphClass = computed(() => [
  'w-4',
  props.iconClass ?? (props.destructive ? 'text-error' : 'text-[var(--primary)]'),
])
</script>

<template>
  <NuxtLink
    v-if="opensPage"
    :to="props.to!"
    :class="[entryClass, 'row-link text-inherit no-underline hover:no-underline']"
  >
    <font-awesome-icon v-if="icon" :icon="icon" :class="glyphClass" />
    <slot />
  </NuxtLink>
  <button
    v-else
    type="button"
    :disabled="disabled"
    :class="entryClass"
    @click="$emit('click')"
  >
    <font-awesome-icon v-if="icon" :icon="icon" :class="glyphClass" />
    <slot />
  </button>
</template>
