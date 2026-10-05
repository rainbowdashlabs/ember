/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import GradingItemButton from './GradingItemButton.vue'
import type {TestProtocolSection} from '@/api/generated/schema'
import type {ProtocolTree} from '../protocolTree'

/**
 * A subsection on the grading sheet with its points and, below them, its own subsections, each drawn
 * by this same component one level deeper. Only what the examiner may grade is shown: a subsection
 * with nothing of theirs anywhere under it is left out.
 */
const props = defineProps<{
  section: TestProtocolSection
  depth: number
  tree: ProtocolTree
  checks: ReadonlyMap<number, boolean>
  gradable: ReadonlySet<number>
  onToggle: (itemId: number) => void
}>()

const shown = computed(() => [...props.tree.subtreeOf(props.section.id)].some(id => props.gradable.has(id)))
const ownPoints = computed(() => (props.gradable.has(props.section.id) ? props.tree.itemsOf(props.section.id) : []))
</script>

<template>
  <div v-if="shown" class="border-t border-[var(--border)] pt-3 mt-3" :class="depth > 1 ? (depth <= 3 ? 'ml-3' : 'ml-1') : ''">
    <SubHeader class="text-sm mb-2">{{ section.name }}</SubHeader>
    <div class="space-y-2">
      <GradingItemButton v-for="item in ownPoints" :key="item.id" :item="item" :checked="!!checks.get(item.id)"
                         @toggle="onToggle(item.id)"/>
    </div>
    <GradingSubsection v-for="child in tree.childrenOf(section.id)" :key="child.id" :section="child" :depth="depth + 1"
                       :tree="tree" :checks="checks" :gradable="gradable" :on-toggle="onToggle"/>
  </div>
</template>
