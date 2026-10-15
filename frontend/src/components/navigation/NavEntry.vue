/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import SidebarGroup from '@/components/navigation/SidebarGroup.vue'
import SidebarLink from '@/components/navigation/SidebarLink.vue'
import SidebarSubGroup from '@/components/navigation/SidebarSubGroup.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {NavNode} from '@/data/sidebar/navTypes'
import {listed} from '@/util/navTree'

/**
 * One entry of a sidebar declared as data, and everything under it.
 *
 * <p>A page is a link. A group is a row with hover flyouts at the top of the collapsed rail, a
 * collapsible subgroup inside a section, and its pages straight in the parent when it is marked
 * inline. Pages kept for the palette are not listed.
 */
const props = defineProps<{
  node: NavNode
  /** Inside a section rather than at the top of the sidebar. */
  nested?: boolean
}>()

const emit = defineEmits<{
  (e: 'navigate'): void
}>()

const children = computed(() => props.node.kind === 'group' ? listed(props.node.children) : [])
</script>

<template>
  <SidebarLink v-if="node.kind === 'page'" :data-onboarding="node.onboarding" :icon="['fas', node.icon]" :name="node.key" :to="node.to"
               :badge="node.badge" :class="node.dimmed ? 'opacity-40' : ''" @navigate="emit('navigate')">
    <span v-if="node.trailingIcon" class="flex items-center gap-1.5">
      {{ node.label }}
      <SecondaryBadge class="shrink-0">
        <font-awesome-icon :icon="['fas', node.trailingIcon]" class="w-2.5 h-2.5"/>
      </SecondaryBadge>
    </span>
    <template v-else>{{ node.label }}</template>
  </SidebarLink>
  <template v-else-if="node.inline">
    <NavEntry v-for="child in children" :key="child.key" :node="child" nested @navigate="emit('navigate')"/>
  </template>
  <SidebarSubGroup v-else-if="nested" :data-onboarding="node.onboarding" :icon="['fas', node.icon]" :label="node.label"
                   :prefix="node.prefix" :badge="node.badge" :remember-as="node.key">
    <template v-for="child in children" :key="child.key">
      <NavEntry :node="child" nested @navigate="emit('navigate')"/>
    </template>
  </SidebarSubGroup>
  <SidebarGroup v-else :data-onboarding="node.onboarding" :icon="['fas', node.icon]" :label="node.label"
                :prefix="node.prefix" :to="node.to" :name="node.key" :badge="node.badge" :group-key="node.key"
                @navigate="emit('navigate')">
    <template v-for="child in children" :key="child.key">
      <NavEntry :node="child" nested @navigate="emit('navigate')"/>
    </template>
  </SidebarGroup>
</template>
