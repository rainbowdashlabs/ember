/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import GradingItemButton from './GradingItemButton.vue'
import GradingSubsection from './GradingSubsection.vue'
import type { TestProtocolSection } from '@/api/generated/schema'
import type { ProtocolTree } from '../protocolTree'

/** One top-level section on the grading sheet, with everything under it the examiner may grade. */
const props = defineProps<{
  section: TestProtocolSection
  tree: ProtocolTree
  checks: ReadonlyMap<number, boolean>
  gradable: ReadonlySet<number>
  score: number
  maxPoints: number
  done: boolean
}>()

const emit = defineEmits<{
  (e: 'toggleCheck', itemId: number): void
  (e: 'toggleDone'): void
}>()

const { t } = useI18n()

const ownPoints = computed(() => (props.gradable.has(props.section.id) ? props.tree.itemsOf(props.section.id) : []))

function toggle(itemId: number) {
  emit('toggleCheck', itemId)
}
</script>

<template>
  <NeutralContainer class="space-y-3 mb-4">
    <div class="flex items-center justify-between">
      <SectionHeader class="font-bold">
        <font-awesome-icon v-if="done" :icon="['fas', 'circle-check']" class="w-4 h-4 text-[var(--color-success)] mr-1" />
        {{ section.name }}
      </SectionHeader>
      <div class="flex items-center gap-2">
        <SuccessBadge>{{ score }} / {{ maxPoints }}P</SuccessBadge>
        <IconButton v-if="!done" icon="check" :label="t('protocol.markDone')" @click="$emit('toggleDone')" />
        <IconButton v-else icon="rotate-left" :label="t('protocol.unmarkDone')" @click="$emit('toggleDone')" />
      </div>
    </div>

    <GradingItemButton
      v-for="item in ownPoints"
      :key="item.id"
      :item="item"
      :checked="!!checks.get(item.id)"
      @toggle="toggle(item.id)"
    />

    <GradingSubsection v-for="sub in tree.childrenOf(section.id)" :key="sub.id" :section="sub" :depth="1" :tree="tree"
                       :checks="checks" :gradable="gradable" :on-toggle="toggle"/>
  </NeutralContainer>
</template>
