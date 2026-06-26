/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type { TestProtocolItem } from '@/api/protocol'

defineProps<{
  item: TestProtocolItem
  canEdit: boolean
}>()

defineEmits<{
  (e: 'edit', item: TestProtocolItem): void
  (e: 'delete', id: number): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center gap-2 pl-4 text-xs">
    <MutedIcon :icon="['fas', 'square']" />
    <div class="flex-1">
      <span>{{ item.label }}</span>
      <p v-if="item.description" class="text-[var(--text-muted)]">{{ item.description }}</p>
    </div>
    <span class="text-xs text-[var(--text-muted)]">{{ item.points }}P</span>
    <template v-if="canEdit">
      <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="$emit('edit', item)" />
      <DeleteButton :label="t('common.delete')" @click="$emit('delete', item.id)" />
    </template>
  </div>
</template>
