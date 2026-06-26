/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import type {ContainerDetail, InventoryContainerKind} from '@/api/inventoryContainers'

const props = defineProps<{
  detail: ContainerDetail
  kindById: Map<number, InventoryContainerKind>
  error: string
}>()

const emit = defineEmits<{
  edit: []
  delete: []
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="flex items-center flex-wrap gap-2 mb-3 text-sm text-(--text-muted)">
      <router-link :to="{name: 'inventory-storage'}" class="hover:underline">
        {{ t('inventory.storage.title') }}
      </router-link>
      <template v-for="(seg, i) in props.detail.pathSegments" :key="i">
        <span>/</span>
        <router-link
            v-if="i < props.detail.pathSegments.length - 1"
            :to="{name: 'inventory-container-detail', params: {id: String(props.detail.pathIds[i])}}"
            class="hover:underline"
        >
          {{ seg }}
        </router-link>
        <span v-else class="text-(--text)">{{ seg }}</span>
      </template>
    </div>

    <div class="flex items-center justify-between mb-4">
      <PageHeader>
        <font-awesome-icon
            :icon="['fas', props.kindById.get(props.detail.container.kindId ?? -1)?.icon ?? 'box']"
            class="mr-2 text-(--text-muted)"
        />
        {{ props.detail.container.name }}
      </PageHeader>
      <div class="flex gap-2">
        <EditButton @click="emit('edit')" :label="t('common.edit')" />
        <DeleteButton @click="emit('delete')" :label="t('common.delete')" />
      </div>
    </div>

    <div v-if="props.error" class="mb-4">
      <Alert variant="error">{{ props.error }}</Alert>
    </div>
  </div>
</template>
