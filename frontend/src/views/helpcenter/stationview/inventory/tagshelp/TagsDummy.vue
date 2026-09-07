/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ItemTagPicker from '@/views/stationview/inventory/ItemTagPicker.vue'
import type {InventoryTag} from '@/api/inventoryTags'

/** The picker as it stands on a piece that already wears one word and is about to wear a second. */
const {t} = useI18n()

const tags: InventoryTag[] = [
    {id: 1, name: 'Funk', color: '#3694FF', position: 0},
    {id: 2, name: 'Gemeinde', color: null, position: 1},
]

const names = ref(['Funk'])
</script>

<template>
    <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('inventory.tag.field') }}</FieldLabel>
        <ItemTagPicker v-model:names="names" :tags="tags"/>
        <p class="text-xs text-(--text-muted)">{{ t('inventory.tag.fieldHint') }}</p>
    </NeutralContainer>
</template>
