/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

/**
 * One icon, drawn from whichever of the two sets names it.
 *
 * <p>The prefix decides: `fas` and `fab` are FontAwesome and go on as they always did, `ph` is
 * Phosphor and names one of the icons registered in the plugin of that name. Every component that
 * takes an `icon` renders it through here, so a screen that needs a picture FontAwesome does not
 * have changes the prefix and nothing else.
 *
 * <p>Phosphor is asked for its `fill` weight, because it stands beside FontAwesome's solid icons and
 * the outline weights read as a second set on the same screen.
 *
 * <p>One root element and not a pair behind `v-if`, so that the height and width a caller passes
 * still reach the icon: Vue hands attributes down only where there is a single root to hand them to.
 */
const props = defineProps<{
  /** `['fas', 'user']` or `['ph', 'fire-truck']`, or a bare FontAwesome name. */
  icon: string | string[]
}>()

const PHOSPHOR = 'ph'

/** The registered component name for a Phosphor icon, or null where FontAwesome draws it. */
const phosphorComponent = computed(() => {
  if (!Array.isArray(props.icon) || props.icon[0] !== PHOSPHOR) return null
  const name = props.icon[1] ?? ''
  return `Ph${name.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')}`
})

const component = computed(() => phosphorComponent.value ?? 'font-awesome-icon')

const attributes = computed(() => phosphorComponent.value ? {weight: 'fill'} : {icon: props.icon})
</script>

<template>
  <component :is="component" v-bind="attributes"/>
</template>
