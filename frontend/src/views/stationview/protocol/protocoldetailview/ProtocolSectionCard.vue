/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DragList from '@/components/input/DragList.vue'
import ProtocolItemRow from './ProtocolItemRow.vue'
import ProtocolSubsectionBlock from './ProtocolSubsectionBlock.vue'
import type { TestProtocolSection, TestProtocolItem } from '@/api/generated/schema'

const props = defineProps<{
  section: TestProtocolSection
  childSections: TestProtocolSection[]
  sectionItems: (id: number) => TestProtocolItem[]
  sectionTotalPoints: (id: number) => number
  canEdit: boolean
}>()

defineEmits<{
  addItem: [sectionId: number]
  addSubsection: [parentId: number]
  editSection: [s: TestProtocolSection]
  deleteSection: [id: number]
  editItem: [item: TestProtocolItem]
  deleteItem: [id: number]
  reorderItems: [sectionId: number, fromIndex: number, toIndex: number]
  reorderSubsections: [parentId: number, fromIndex: number, toIndex: number]
}>()

const { t } = useI18n()
void props
</script>

<template>
  <NeutralContainer class="space-y-2">
    <div class="flex items-center gap-2">
      <SubHeader>{{ section.name }}</SubHeader>
      <MutedText class="ml-auto">{{ sectionTotalPoints(section.id) }}P</MutedText>
      <template v-if="canEdit">
        <IconButton :icon="['fas', 'plus']" :label="t('protocol.addItem')" @click="$emit('addItem', section.id)" />
        <IconButton :icon="['fas', 'folder-plus']" :label="t('protocol.addSubsection')" @click="$emit('addSubsection', section.id)" />
        <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="$emit('editSection', section)" />
        <DeleteButton :label="t('common.delete')" @click="$emit('deleteSection', section.id)" />
      </template>
    </div>
    <MutedText v-if="section.description" tag="p" size="sm">{{ section.description }}</MutedText>

    <DragList
        :items="sectionItems(section.id)"
        :key-fn="(item) => item.id"
        :disabled="!canEdit"
        class="space-y-2"
        @reorder="(from, to) => $emit('reorderItems', section.id, from, to)"
    >
      <template #default="{item}">
        <ProtocolItemRow
          :item="item"
          :can-edit="canEdit"
          @edit="(i) => $emit('editItem', i)"
          @delete="(id) => $emit('deleteItem', id)"
        />
      </template>
    </DragList>

    <DragList
        :items="childSections"
        :key-fn="(sub) => sub.id"
        :disabled="!canEdit"
        class="space-y-2"
        @reorder="(from, to) => $emit('reorderSubsections', section.id, from, to)"
    >
      <template #default="{item: sub}">
        <ProtocolSubsectionBlock
          :sub="sub"
          :items="sectionItems(sub.id)"
          :total-points="sectionTotalPoints(sub.id)"
          :can-edit="canEdit"
          @add-item="(id) => $emit('addItem', id)"
          @edit-section="(s) => $emit('editSection', s)"
          @delete-section="(id) => $emit('deleteSection', id)"
          @edit-item="(i) => $emit('editItem', i)"
          @delete-item="(id) => $emit('deleteItem', id)"
          @reorder-items="(id, from, to) => $emit('reorderItems', id, from, to)"
        />
      </template>
    </DragList>
  </NeutralContainer>
</template>
