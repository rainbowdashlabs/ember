/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ProtocolItemRow from './ProtocolItemRow.vue'
import type { TestProtocolSection, TestProtocolItem } from '@/api/protocol'

defineProps<{
  sub: TestProtocolSection
  items: TestProtocolItem[]
  totalPoints: number
  canEdit: boolean
}>()

defineEmits<{
  (e: 'addItem', sectionId: number): void
  (e: 'editSection', s: TestProtocolSection): void
  (e: 'deleteSection', id: number): void
  (e: 'editItem', item: TestProtocolItem): void
  (e: 'deleteItem', id: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="ml-4 border-l-2 border-[var(--border)] pl-3 space-y-1">
    <div class="flex items-center gap-2">
      <span class="font-medium text-sm">{{ sub.name }}</span>
      <MutedText class="ml-auto">{{ totalPoints }}P</MutedText>
      <template v-if="canEdit">
        <IconButton :icon="['fas', 'plus']" :label="t('protocol.addItem')" @click="$emit('addItem', sub.id)" />
        <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="$emit('editSection', sub)" />
        <DeleteButton :label="t('common.delete')" @click="$emit('deleteSection', sub.id)" />
      </template>
    </div>
    <MutedText v-if="sub.description" tag="p" size="sm">{{ sub.description }}</MutedText>
    <ProtocolItemRow
      v-for="item in items"
      :key="item.id"
      :item="item"
      :can-edit="canEdit"
      @edit="(i) => $emit('editItem', i)"
      @delete="(id) => $emit('deleteItem', id)"
    />
  </div>
</template>
