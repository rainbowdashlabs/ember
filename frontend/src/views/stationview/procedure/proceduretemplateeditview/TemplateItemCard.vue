/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import IconButton from '@/components/button/IconButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type { ProcedureTemplateItem } from '@/api/procedures'

const { t } = useI18n()

const props = defineProps<{
  item: ProcedureTemplateItem
  index: number
  canManage: boolean
  dependencies: number[]
  getItemById: (id: number) => ProcedureTemplateItem | undefined
}>()

const emit = defineEmits<{
  (e: 'edit', item: ProcedureTemplateItem): void
  (e: 'delete', itemId: number): void
  (e: 'openDeps', item: ProcedureTemplateItem): void
  (e: 'removeDep', depId: number, itemId: number): void
}>()
</script>

<template>
  <NeutralContainer class="group">
    <div class="flex items-start gap-3">
      <span class="text-sm font-mono text-[var(--text-muted)] pt-0.5 shrink-0">{{ index + 1 }}.</span>
      <div class="flex-1 min-w-0">
        <div class="font-medium">{{ item.title }}</div>
        <div v-if="item.description" class="text-sm text-[var(--text-muted)]">{{ item.description }}</div>
        <div class="flex flex-wrap gap-2 mt-1 text-xs text-[var(--text-muted)]">
          <span v-if="item.isPublic">
            <font-awesome-icon :icon="['fas', 'eye']" class="w-3 h-3 mr-0.5" /> {{ t('procedures.itemPublic') }}
          </span>
          <span v-else>
            <font-awesome-icon :icon="['fas', 'eye-slash']" class="w-3 h-3 mr-0.5" /> {{ t('procedures.itemPrivate') }}
          </span>
          <span v-if="item.userAssigned">
            <font-awesome-icon :icon="['fas', 'user']" class="w-3 h-3 mr-0.5" /> {{ t('procedures.itemUserAssigned') }}
          </span>
        </div>
        <!-- Dependencies list -->
        <div v-if="dependencies.length > 0" class="mt-2">
          <span class="text-xs font-medium text-[var(--text-muted)]">{{ t('procedures.dependsOn') }}:</span>
          <div class="flex flex-wrap gap-1 mt-1">
            <span
              v-for="depId in dependencies"
              :key="depId"
              class="inline-flex items-center gap-1 text-xs bg-[var(--bg-light-accent)] dark:bg-[var(--bg-dark-accent)] rounded px-1.5 py-0.5"
            >
              {{ getItemById(depId)?.title ?? depId }}
              <IconButton
                v-if="canManage"
                :icon="['fas', 'xmark']"
                label="Remove"
                class="!p-0 !w-3 !h-3 text-[var(--text-muted)]"
                @click="emit('removeDep', depId, item.id)"
              />
            </span>
          </div>
        </div>
      </div>
      <div v-if="canManage" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
        <IconButton :icon="['fas', 'link']" :label="t('procedures.dependencies')" @click="emit('openDeps', item)" />
        <EditButton :label="t('common.edit')" @click="emit('edit', item)" />
        <DeleteButton :label="t('procedures.deleteItem')" @click="emit('delete', item.id)" />
      </div>
    </div>
  </NeutralContainer>
</template>
