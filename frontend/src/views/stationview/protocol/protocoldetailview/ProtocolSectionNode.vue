/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DragList from '@/components/input/DragList.vue'
import ProtocolItemRow from './ProtocolItemRow.vue'
import ProtocolSectionHeader from './ProtocolSectionHeader.vue'
import type {TestProtocolSection} from '@/api/generated/schema'
import {useProtocolEditor} from './protocolEditor'

/**
 * One section of the protocol editor with its points and, below them, its subsections, each drawn by
 * this same component one level deeper. A top-level section is a card; deeper ones hang off a rule at
 * the left, indented less once the depth would squeeze a phone screen.
 */
const props = defineProps<{
  section: TestProtocolSection
  depth: number
}>()

const editor = useProtocolEditor()

const isCut = computed(() => editor.cut.value?.id === props.section.id)
const indent = computed(() => (props.depth === 0 ? '' : props.depth <= 3 ? 'ml-4' : 'ml-1'))
</script>

<template>
  <component
      :is="depth === 0 ? NeutralContainer : 'div'"
      :class="[depth === 0 ? 'space-y-2' : `${indent} border-l-2 border-[var(--border)] pl-3 space-y-1`,
               isCut ? 'opacity-50' : '']"
  >
    <ProtocolSectionHeader :section="section" :depth="depth"/>
    <MutedText v-if="section.description" tag="p" size="sm">{{ section.description }}</MutedText>

    <DragList
        :items="editor.tree.value.itemsOf(section.id)"
        :key-fn="(item) => item.id"
        :disabled="!editor.canEdit.value"
        class="space-y-1"
        @reorder="(from, to) => editor.reorderItems(section.id, from, to)"
    >
      <template #default="{item}">
        <ProtocolItemRow :item="item" :can-edit="editor.canEdit.value" @edit="editor.editItem"
                         @delete="editor.deleteItem"/>
      </template>
    </DragList>

    <DragList
        :items="editor.tree.value.childrenOf(section.id)"
        :key-fn="(child) => child.id"
        :disabled="!editor.canEdit.value"
        class="space-y-2"
        @reorder="(from, to) => editor.reorderSections(section.id, from, to)"
    >
      <template #default="{item: child}">
        <ProtocolSectionNode :section="child" :depth="depth + 1"/>
      </template>
    </DragList>
  </component>
</template>
