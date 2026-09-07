/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import type {ItemNameCount} from '@/api/inventoryArts'

/**
 * The distinct names written on the pieces, commonest first.
 *
 * <p>This is the view that shows a station where its typos are. Six rows saying one thing and one
 * row saying nearly the same thing sit next to each other here and nowhere else.
 */
defineProps<{
  names: ItemNameCount[]
  selected: Set<string>
}>()

const emit = defineEmits<{
  toggle: [name: string]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="tidy-names">
    <ul class="divide-y divide-(--border)">
      <li v-for="row in names" :key="row.name" class="flex items-center gap-3 py-2">
        <CheckboxInput
            :model-value="selected.has(row.name)"
            :data-testid="`tidy-name-${row.name}`"
            @update:model-value="emit('toggle', row.name)"
        />
        <div class="min-w-0 flex-1">
          <p class="truncate text-sm">{{ row.name }}</p>
          <p class="truncate text-xs text-(--text-muted)">
            {{ t('inventory.art.nameCount', {pieces: row.pieces, unassigned: row.unassigned}) }}
          </p>
        </div>
      </li>
    </ul>
  </NeutralContainer>
</template>
