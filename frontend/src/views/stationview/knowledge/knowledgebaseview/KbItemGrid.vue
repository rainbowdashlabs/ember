/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import KbItemTile from './KbItemTile.vue'
import type {KbItem} from './useKbItems'

const props = withDefaults(defineProps<{
    items: KbItem[]
    /** Whether entries can be marked at all, which only the browsed folder allows. */
    selecting?: boolean
    selectedKeys?: string[]
}>(), {selecting: false, selectedKeys: () => []})

const emit = defineEmits<{
    toggleSelect: [key: string, value: boolean, shift: boolean]
}>()

/** A partner station's entry cannot be acted on here, so it never carries a box. */
function markable(item: KbItem): boolean {
    return props.selecting && (item.key.startsWith('folder-') || item.key.startsWith('file-'))
}
</script>

<template>
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        <KbItemTile
            v-for="item in items"
            :key="item.key"
            :item="item"
            :selectable="markable(item)"
            :selected="selectedKeys.includes(item.key)"
            @toggle-select="(key, value, shift) => emit('toggleSelect', key, value, shift)"
        />
    </div>
</template>
