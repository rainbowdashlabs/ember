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
import IconButton from '@/components/button/IconButton.vue'
import type {ChecklistColumnDto} from '@/api/checklists'

const columns = defineModel<ChecklistColumnDto[]>({required: true})

const {t} = useI18n()

function onReorder(fromIndex: number, toIndex: number) {
  const [moved] = columns.value.splice(fromIndex, 1)
  columns.value.splice(toIndex, 0, moved)
}

function moveUp(index: number) {
  if (index <= 0) return
  onReorder(index, index - 1)
}

function moveDown(index: number) {
  if (index >= columns.value.length - 1) return
  onReorder(index, index + 1)
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
          <font-awesome-icon :icon="['fas', 'grip-vertical']" class="hidden sm:inline text-(--text-muted) cursor-grab active:cursor-grabbing"/>
          <span class="text-xs text-(--text-muted) tabular-nums w-6">{{ index + 1 }}.</span>
          <div class="flex-1 min-w-0">
            <div class="font-medium truncate">{{ item.label }}</div>
            <div v-if="item.description" class="text-xs text-(--text-muted) truncate">{{ item.description }}</div>
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <IconButton
                :icon="['fas', 'arrow-up']"
                :label="t('checklist.moveColumnUp')"
                :disabled="index === 0"
                @click="moveUp(index)"
            />
            <IconButton
                :icon="['fas', 'arrow-down']"
                :label="t('checklist.moveColumnDown')"
                :disabled="index === columns.length - 1"
                @click="moveDown(index)"
            />
          </div>
        </div>
      </template>
    </DragList>
  </div>
</template>
