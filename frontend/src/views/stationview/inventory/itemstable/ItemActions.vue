/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import type { InventoryItem } from '@/api/generated/schema'
import type { InventoryItemActionEmits } from '../itemEmits'

/**
 * The menu of one piece in an inventory's list.
 *
 * <p>What the station would be refused for is not offered: a piece lent out or missing is not handed
 * out, and a piece an open movement carries is the movement's to move, so it is neither handed out,
 * taken back, reported missing nor deleted from here.
 */
const props = withDefaults(defineProps<{
  item: InventoryItem
  showActions?: boolean
  lentOut?: boolean
  /** Whether an open movement carries the piece, setting out or promised. */
  onMovement?: boolean
}>(), {
  showActions: false,
  lentOut: false,
  onMovement: false,
})

/** Whether the piece is free for the station to act on at all: not lent out and not on a movement. */
const free = computed(() => !props.lentOut && !props.onMovement)

const emit = defineEmits<InventoryItemActionEmits>()

const { t } = useI18n()
</script>

<template>
  <ActionsMenu :label="t('common.actions')">
    <template v-if="showActions">
      <DropdownMenuItem v-if="!item.lostAt && free" :icon="['fas', 'user']"
                        @click="emit('assign', item)">
        {{ item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="item.assignedTo && !item.lostAt && free" :icon="['fas', 'right-from-bracket']"
                        icon-class="text-(--text-muted)" @click="emit('unassign', item)">
        {{ t('inventory.edit.unassign') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="!item.lostAt && free" :icon="['fas', 'triangle-exclamation']"
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
      <DropdownMenuItem v-if="free" :icon="['fas', 'trash']" destructive
                        @click="emit('delete', item)">
        {{ t('common.delete') }}
      </DropdownMenuItem>
    </template>
  </ActionsMenu>
</template>
