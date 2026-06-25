/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type { InventoryItem } from '@/api/types'
import type { InventoryItemActionEmits } from '../itemEmits'

withDefaults(defineProps<{
  item: InventoryItem
  showActions?: boolean
  lentOut?: boolean
}>(), {
  showActions: false,
  lentOut: false,
})

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
</script>

<template>
  <template v-if="showActions">
    <IconButton v-if="!item.lostAt && !lentOut" :icon="['fas', 'user']"
                :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')"
                class="text-primary hover:bg-primary/15" @click="emit('assign', item)"/>
    <IconButton v-if="item.assignedTo && !item.lostAt && !lentOut" :icon="['fas', 'right-from-bracket']"
                :label="t('inventory.edit.unassign')"
                class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                @click="emit('unassign', item)"/>
    <IconButton v-if="!item.lostAt && !lentOut" :icon="['fas', 'triangle-exclamation']"
                :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15"
                @click="emit('markLost', item)"/>
    <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')"
                class="text-success hover:bg-success/15" @click="emit('markFound', item)"/>
  </template>
  <IconButton :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')"
              class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
              @click="emit('history', item)"/>
  <template v-if="showActions">
    <EditButton @click="emit('edit', item)"/>
    <DeleteButton v-if="!lentOut" @click="emit('delete', item)"/>
  </template>
</template>
