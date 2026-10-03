/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {Placeholder} from '@/api/generated/schema'
import type {PlaceholderNode} from './placeholderTree'
import PlaceholderRow from './PlaceholderRow.vue'

/**
 * The entries of one step of the picker's path: the steps below it and the placeholders it holds.
 */
defineProps<{
  nodes: PlaceholderNode[]
}>()

const emit = defineEmits<{
  open: [name: string]
  pick: [placeholder: Placeholder]
}>()

function choose(node: PlaceholderNode) {
  if (node.kind === 'branch') emit('open', node.name)
  else emit('pick', node.placeholder)
}
</script>

<template>
  <ul class="grid max-h-56 gap-0.5 overflow-y-auto sm:grid-cols-2" data-testid="placeholder-picker-level">
    <li v-for="node in nodes" :key="`${node.kind}:${node.name}`">
      <PlaceholderRow :name="node.name" :branch="node.kind === 'branch'"
                      :data-testid="node.kind === 'leaf' ? `placeholder-${node.placeholder.key}` : 'placeholder-branch'"
                      @choose="choose(node)"/>
    </li>
  </ul>
</template>
