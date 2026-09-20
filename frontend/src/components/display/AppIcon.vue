/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {iconSetOf, phosphorComponentOf, type IconSet} from '@/util/iconName'

/**
 * One icon, drawn from whichever of the two sets names it.
 *
 * <p>The prefix decides: `fas` and `fab` are FontAwesome and go on as they always did, `ph` is
 * Phosphor and names one of the icons registered in the plugin of that name. Every component that
 * takes an `icon` renders it through here, so a screen that needs a picture FontAwesome does not
 * have changes the prefix and nothing else.
 *
 * <p>A stored name is taken as it stands, prefix and all, so that whatever a kind or an inventory
 * keeps can be handed straight over. Asking each call site to split it first is what left one of
 * them handing a Phosphor pair to FontAwesome, which drew nothing.
 *
 * <p>Phosphor is asked for its `fill` weight, because it stands beside FontAwesome's solid icons and
 * the outline weights read as a second set on the same screen.
 *
 * <p>One root element and not a pair behind `v-if`, so that the height and width a caller passes
 * still reach the icon: Vue hands attributes down only where there is a single root to hand them to.
 */
const props = defineProps<{
  /** `['fas', 'user']`, `['ph', 'fire-truck']`, a stored `ph:fire-truck`, or a bare FontAwesome name. */
  icon: string | string[]
}>()

/** The set and the name in it, however the caller chose to write them. */
const named = computed<{set: IconSet; name: string}>(() => {
  const icon = props.icon
  if (!Array.isArray(icon)) return iconSetOf(icon)
  return {set: (icon[0] ?? 'fas') as IconSet, name: icon[1] ?? ''}
})

/** The registered component name for a Phosphor icon, or null where FontAwesome draws it. */
const phosphorComponent = computed(() =>
  named.value.set === 'ph' ? phosphorComponentOf(named.value.name) : null)

const component = computed(() => phosphorComponent.value ?? 'font-awesome-icon')

const attributes = computed(() => phosphorComponent.value
  ? {weight: 'fill'}
  : {icon: [named.value.set, named.value.name]})
</script>

<template>
  <component :is="component" v-bind="attributes"/>
</template>
