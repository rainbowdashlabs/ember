/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
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
  <ActionsMenu :label="t('common.actions')">
    <template v-if="showActions">
      <DropdownMenuItem v-if="!item.lostAt && !lentOut" :icon="['fas', 'user']"
                        @click="emit('assign', item)">
        {{ item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="item.assignedTo && !item.lostAt && !lentOut" :icon="['fas', 'right-from-bracket']"
                        icon-class="text-(--text-muted)" @click="emit('unassign', item)">
        {{ t('inventory.edit.unassign') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="!item.lostAt && !lentOut" :icon="['fas', 'triangle-exclamation']"
                        icon-class="text-error" class="text-error" @click="emit('markLost', item)">
        {{ t('inventory.edit.markLost') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="item.lostAt" :icon="['fas', 'check']"
                        icon-class="text-success" @click="emit('markFound', item)">
        {{ t('inventory.edit.markFound') }}
      </DropdownMenuItem>
    </template>
    <DropdownMenuItem :icon="['fas', 'clock-rotate-left']" icon-class="text-(--text-muted)"
                      @click="emit('history', item)">
      {{ t('inventory.edit.historyTitle') }}
    </DropdownMenuItem>
    <template v-if="showActions">
      <DropdownMenuItem :icon="['fas', 'pen']" @click="emit('edit', item)">
        {{ t('common.edit') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="!lentOut" :icon="['fas', 'trash']"
                        icon-class="text-error" class="text-error" @click="emit('delete', item)">
        {{ t('common.delete') }}
      </DropdownMenuItem>
    </template>
  </ActionsMenu>
</template>
