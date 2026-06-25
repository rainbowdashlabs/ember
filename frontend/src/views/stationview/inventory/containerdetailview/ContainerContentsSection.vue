/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import ContainerContentsTree from '@/views/stationview/inventory/storageview/ContainerContentsTree.vue'
import type {ContainerContents, InventoryContainerKind} from '@/api/inventoryContainers'

const recursive = defineModel<boolean>('recursive', {required: true})

const props = defineProps<{
  contents: ContainerContents | null
  rootContainerId: number
  kindById: Map<number, InventoryContainerKind>
  scanBusy: boolean
}>()

const emit = defineEmits<{
  scan: [value: string]
  addChild: []
  openContainer: [id: number]
  openItem: [id: number]
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-2">
      <SectionHeader>{{ t('inventory.storage.contents') }}</SectionHeader>
      <div class="flex items-center gap-2 text-sm">
        <label class="flex items-center gap-1">
          <ToggleInput v-model="recursive" />
          <span>{{ t('inventory.storage.recursive') }}</span>
        </label>
        <ScanButton mode="continuous" :disabled="props.scanBusy" @decoded="(v: string) => emit('scan', v)" />
        <PrimaryButton size="sm" @click="emit('addChild')">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
          {{ t('inventory.storage.addChild') }}
        </PrimaryButton>
      </div>
    </div>

    <NeutralContainer class="mb-6">
      <template v-if="props.contents">
        <ContainerContentsTree
            v-if="recursive"
            :root-container-id="props.rootContainerId"
            :containers="props.contents.children"
            :items="props.contents.items"
            :kind-by-id="props.kindById"
            @open-container="(id: number) => emit('openContainer', id)"
            @open-item="(id: number) => emit('openItem', id)"
        />
        <div v-else-if="props.contents.children.length > 0 || props.contents.items.length > 0" class="flex flex-col gap-4">
          <div v-if="props.contents.children.length > 0">
            <SubHeader class="mb-2">{{ t('inventory.storage.childContainers') }}</SubHeader>
            <ul class="divide-y divide-(--bg-accent)">
              <li
                  v-for="c in props.contents.children"
                  :key="c.id"
                  class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                  @click="emit('openContainer', c.id)"
              >
                <font-awesome-icon :icon="['fas', props.kindById.get(c.kindId ?? -1)?.icon ?? 'box']" class="w-4 text-(--text-muted)" />
                <span class="font-medium">{{ c.name }}</span>
                <span v-if="c.internalId" class="text-xs text-(--text-muted)">{{ c.internalId }}</span>
              </li>
            </ul>
          </div>
          <div v-if="props.contents.items.length > 0">
            <SubHeader class="mb-2">{{ t('inventory.storage.containedItems') }}</SubHeader>
            <ul class="divide-y divide-(--bg-accent)">
              <li
                  v-for="i in props.contents.items"
                  :key="i.id"
                  class="py-2 flex items-center gap-3 cursor-pointer hover:bg-(--bg-accent) rounded-theme px-2"
                  @click="emit('openItem', i.id)"
              >
                <font-awesome-icon :icon="['fas', 'cube']" class="w-4 text-(--text-muted)" />
                <span class="font-medium">{{ i.name ?? '' }}</span>
                <span v-if="i.internalId" class="text-xs text-(--text-muted)">{{ i.internalId }}</span>
              </li>
            </ul>
          </div>
        </div>
        <EmptyState v-else :message="t('inventory.storage.contentsEmpty')" />
      </template>
    </NeutralContainer>
  </div>
</template>
