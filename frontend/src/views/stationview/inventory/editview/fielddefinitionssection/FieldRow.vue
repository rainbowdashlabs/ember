/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type {InventoryFieldDefinition} from '@/api/inventoryFields'

defineProps<{
  field: InventoryFieldDefinition
  mode: 'drag' | 'arrows'
  canMoveUp?: boolean
  canMoveDown?: boolean
}>()

const emit = defineEmits<{
  edit: []
  delete: []
  moveUp: []
  moveDown: []
}>()

const {t} = useI18n()
</script>

<template>
  <div
      :class="['py-2 flex items-center gap-3 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50',
               mode === 'drag' ? 'cursor-grab active:cursor-grabbing' : '']">
    <MutedIcon v-if="mode === 'drag'" size="md" :icon="['fas', 'grip-vertical']"/>
    <div v-else class="flex flex-col">
      <IconButton
          :icon="['fas', 'chevron-up']"
          :label="t('inventory.fields.moveUp')"
          :disabled="!canMoveUp"
          @click="emit('moveUp')"
      />
      <IconButton
          :icon="['fas', 'chevron-down']"
          :label="t('inventory.fields.moveDown')"
          :disabled="!canMoveDown"
          @click="emit('moveDown')"
      />
    </div>
    <span class="font-medium">{{ field.label }}</span>
    <span class="text-xs text-(--text-muted)">{{ t(`inventory.fields.types.${field.fieldType}`) }}</span>
    <span v-if="field.required" class="text-xs text-error">{{ t('inventory.fields.required') }}</span>
    <div class="ml-auto flex gap-2">
      <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="emit('edit')"/>
      <DeleteButton :label="t('common.delete')" @click="emit('delete')"/>
    </div>
  </div>
</template>
