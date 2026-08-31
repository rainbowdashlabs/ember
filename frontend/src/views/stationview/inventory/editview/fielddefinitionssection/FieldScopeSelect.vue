/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {InventoryItem} from '@/api/inventory'
import type {InventoryArt} from '@/api/inventoryArts'
import type {DraftField} from './types'

/**
 * What a field describes: everything in the inventory, one kind of thing, or one single piece.
 *
 * The narrow levels are the point of the mechanism rather than a refinement of it. A frequency band
 * is nonsense on a charging station sharing a drawer with six radios, and a plate number belongs to
 * one vehicle and nothing else. The level is settled when the field is written down and cannot be
 * moved afterwards, the same way its key and its type cannot.
 */
const props = defineProps<{
    draft: DraftField
    arts: InventoryArt[]
    items: InventoryItem[]
}>()

const {t} = useI18n()

const level = computed(() => {
    if (props.draft.itemId != null) return 'ITEM'
    if (props.draft.artId != null) return 'ART'
    return 'INVENTORY'
})

function onLevelChanged(value: string | number | null | undefined) {
    const next = String(value ?? 'INVENTORY')
    if (next === 'ART') {
        props.draft.artId = props.arts[0]?.id ?? null
        props.draft.itemId = null
    } else if (next === 'ITEM') {
        props.draft.artId = null
        props.draft.itemId = props.items[0]?.id ?? null
    } else {
        props.draft.artId = null
        props.draft.itemId = null
    }
}

function itemLabel(item: InventoryItem): string {
    const name = item.name?.trim() || t('inventory.fields.scopeUnnamedPiece')
    return item.internalId ? `${name} (${item.internalId})` : name
}
</script>

<template>
    <label class="flex flex-col gap-1 text-sm md:col-span-2">
        <span>{{ t('inventory.fields.scope') }}</span>
        <SelectInput :model-value="level" :disabled="!!props.draft.id" @update:model-value="onLevelChanged">
            <option value="INVENTORY">{{ t('inventory.fields.scopeInventory') }}</option>
            <option v-if="props.arts.length > 0" value="ART">{{ t('inventory.fields.scopeArt') }}</option>
            <option v-if="props.items.length > 0" value="ITEM">{{ t('inventory.fields.scopeItem') }}</option>
        </SelectInput>
        <SelectInput v-if="level === 'ART'" v-model="props.draft.artId" :disabled="!!props.draft.id">
            <option v-for="art in props.arts" :key="art.id" :value="art.id">{{ art.name }}</option>
        </SelectInput>
        <SelectInput v-if="level === 'ITEM'" v-model="props.draft.itemId" :disabled="!!props.draft.id">
            <option v-for="item in props.items" :key="item.id" :value="item.id">{{ itemLabel(item) }}</option>
        </SelectInput>
        <span class="text-xs text-(--text-muted)">{{ t('inventory.fields.scopeHint') }}</span>
    </label>
</template>
