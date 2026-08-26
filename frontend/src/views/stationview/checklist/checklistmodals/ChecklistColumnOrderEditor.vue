/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import DragList from '@/components/input/DragList.vue'
import type {ChecklistColumnDto} from '@/api/checklists'
import {moveWithin} from '@/util/reorder'

const columns = defineModel<ChecklistColumnDto[]>({required: true})

const {t} = useI18n()

function onReorder(fromIndex: number, toIndex: number) {
  columns.value = moveWithin(columns.value, fromIndex, toIndex)
}
</script>

<template>
  <div>
    <FieldLabel>{{ t('checklist.columnOrder') }}</FieldLabel>
    <p class="text-xs text-(--text-muted) mb-2">{{ t('checklist.columnOrderHint') }}</p>
    <EmptyState v-if="columns.length === 0">
      {{ t('checklist.columnsEmpty') }}
    </EmptyState>
    <DragList
        v-else
        :items="columns"
        :key-fn="(c) => c.id"
        @reorder="onReorder"
    >
      <template #default="{item, index}">
        <div class="flex items-center gap-2 px-2 py-1.5 border border-bg-light-accent dark:border-bg-dark-accent rounded-theme bg-bg-light dark:bg-bg-dark">
          <span class="text-xs text-(--text-muted) tabular-nums w-6">{{ index + 1 }}.</span>
          <div class="flex-1 min-w-0">
            <div class="font-medium truncate">{{ item.label }}</div>
            <div v-if="item.description" class="text-xs text-(--text-muted) truncate">{{ item.description }}</div>
          </div>
        </div>
      </template>
    </DragList>
  </div>
</template>
