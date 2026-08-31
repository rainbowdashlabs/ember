/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {InventoryCollection} from '@/api/inventoryCollections'

defineProps<{
  collections: InventoryCollection[]
  selectedId: number | null
  editable: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  create: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <PrimaryButton
        v-if="editable"
        :icon="['fas', 'plus']"
        full-width
        data-testid="collection-create"
        @click="emit('create')"
    >
      {{ t('inventory.collections.create') }}
    </PrimaryButton>

    <EmptyState v-if="collections.length === 0">{{ t('inventory.collections.empty') }}</EmptyState>

    <SecondaryButton
        v-for="collection in collections"
        :key="collection.id"
        full-width
        class="justify-between gap-2"
        :class="collection.id === selectedId ? 'ring-2 ring-primary' : ''"
        data-testid="collection-entry"
        @click="emit('select', collection.id)"
    >
      <span class="truncate">{{ collection.name }}</span>
      <SecondaryBadge>{{ t('inventory.collections.lineCount', {count: collection.lineCount}) }}</SecondaryBadge>
    </SecondaryButton>
  </NeutralContainer>
</template>
