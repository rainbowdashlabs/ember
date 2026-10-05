/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, provide} from 'vue'
import {useI18n} from 'vue-i18n'
import DragList from '@/components/input/DragList.vue'
import ProtocolSectionNode from '@/views/stationview/protocol/protocoldetailview/ProtocolSectionNode.vue'
import ProtocolCutBar from '@/views/stationview/protocol/protocoldetailview/ProtocolCutBar.vue'
import {protocolEditorKey} from '@/views/stationview/protocol/protocoldetailview/protocolEditor'
import {useSectionCut} from '@/views/stationview/protocol/protocoldetailview/useSectionCut'
import {demoProtocolSections, demoProtocolTree} from '../fixtures'

/**
 * The sample protocol drawn by the editor's own sections, with every action doing nothing. Given a
 * section to show as cut, it shows the bar above the protocol and the paste buttons the editor offers
 * while that section waits to be moved.
 */
const props = defineProps<{
  cutSectionId?: number
}>()

const {t} = useI18n()
const tree = computed(() => demoProtocolTree(t))
const sectionCut = useSectionCut(tree, () => Promise.resolve())
const cutSection = demoProtocolSections(t).find(section => section.id === props.cutSectionId)
if (cutSection) sectionCut.start(cutSection)

const nothing = () => undefined

provide(protocolEditorKey, {
  tree,
  canEdit: computed(() => true),
  cut: sectionCut.cut,
  addItem: nothing,
  addSubsection: nothing,
  editSection: nothing,
  deleteSection: nothing,
  editItem: nothing,
  deleteItem: nothing,
  reorderItems: nothing,
  reorderSections: nothing,
  cutSection: nothing,
  canPasteInto: sectionCut.canPasteInto,
  pasteInto: nothing,
})
</script>

<template>
  <div>
    <ProtocolCutBar v-if="sectionCut.cut.value" class="mb-4" :section="sectionCut.cut.value"
                    :can-paste-at-top="sectionCut.canPasteInto(null)"/>
    <DragList :items="tree.childrenOf(null)" :key-fn="(section) => section.id" class="space-y-4">
      <template #default="{item: section}">
        <ProtocolSectionNode :section="section" :depth="0"/>
      </template>
    </DragList>
  </div>
</template>
