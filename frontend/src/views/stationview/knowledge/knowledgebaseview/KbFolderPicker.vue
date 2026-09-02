/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {KbAccessLevel, levelCovers, type KbFolderTreeEntry} from '@/api/knowledgeBase'

/**
 * Picks a folder anywhere in the station's wiki, the tree root included.
 *
 * The tree is drawn as one indented list rather than as folders that open: a reader looking for
 * somewhere to put something wants to see where the places are, and a list that has to be unfolded
 * hides exactly that. A search box narrows it once the tree grows past what fits on a screen, and a
 * match keeps the folders above it so the row still says where it is.
 *
 * Folders the reader may not write in are shown and cannot be picked. Leaving them out would make
 * the tree lie about its own shape, and the server refuses them anyway.
 */
const props = defineProps<{
    folders: KbFolderTreeEntry[]
    /** The folder being moved and everything under it, which cannot receive themselves. */
    excludeIds?: number[]
}>()

const model = defineModel<number | null>({default: null})

const {t} = useI18n()

const query = ref('')

interface Row extends KbFolderTreeEntry {
    depth: number
}

const rows = computed<Row[]>(() => {
    const byParent = new Map<number | null, KbFolderTreeEntry[]>()
    for (const folder of props.folders) {
        const siblings = byParent.get(folder.parentId ?? null) ?? []
        siblings.push(folder)
        byParent.set(folder.parentId ?? null, siblings)
    }
    for (const siblings of byParent.values()) siblings.sort((a, b) => a.name.localeCompare(b.name))

    const ordered: Row[] = []
    const walk = (parentId: number | null, depth: number) => {
        for (const folder of byParent.get(parentId) ?? []) {
            ordered.push({...folder, depth})
            walk(folder.id, depth + 1)
        }
    }
    walk(null, 0)
    return ordered
})

const visibleRows = computed(() => {
    const term = query.value.trim().toLowerCase()
    if (!term) return rows.value
    const matched = new Set<number>()
    const byId = new Map(props.folders.map(folder => [folder.id, folder]))
    for (const folder of props.folders) {
        if (!folder.name.toLowerCase().includes(term)) continue
        let current: KbFolderTreeEntry | undefined = folder
        while (current && !matched.has(current.id)) {
            matched.add(current.id)
            current = current.parentId != null ? byId.get(current.parentId) : undefined
        }
    }
    return rows.value.filter(row => matched.has(row.id))
})

function selectable(row: Row): boolean {
    if (props.excludeIds?.includes(row.id)) return false
    return levelCovers(row.level, KbAccessLevel.WRITE)
}

function pick(row: Row) {
    if (!selectable(row)) return
    model.value = row.id
}
</script>

<template>
    <div class="flex flex-col gap-2">
        <SearchInput v-if="rows.length > 8" v-model="query" :placeholder="t('kb.movePickerSearch')"/>

        <div class="max-h-64 overflow-y-auto rounded-theme border border-(--border)" data-testid="kb-folder-picker">
            <button
                type="button"
                class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm hover:bg-(--bg-accent)"
                :class="model === null ? 'bg-(--bg-accent) font-medium' : ''"
                data-testid="kb-folder-picker-root"
                @click="model = null"
            >
                <font-awesome-icon :icon="['fas', 'house']" class="text-xs text-(--accent)"/>
                {{ t('kb.root') }}
            </button>

            <button
                v-for="row in visibleRows"
                :key="row.id"
                type="button"
                class="flex w-full items-center gap-2 px-3 py-1.5 text-left text-sm"
                :class="[
                    model === row.id ? 'bg-(--bg-accent) font-medium' : '',
                    selectable(row) ? 'hover:bg-(--bg-accent)' : 'opacity-50 cursor-not-allowed',
                ]"
                :style="{paddingLeft: `${0.75 + row.depth * 1.1}rem`}"
                :title="selectable(row) ? undefined : t('kb.moveTargetReadOnly')"
                :data-testid="`kb-folder-option-${row.id}`"
                @click="pick(row)"
            >
                <font-awesome-icon :icon="['fas', 'folder']" class="text-xs text-(--accent)"/>
                <span class="truncate">{{ row.name }}</span>
            </button>

            <MutedText v-if="visibleRows.length === 0" tag="p" size="sm" class="px-3 py-2">
                {{ t('kb.moveNoFolders') }}
            </MutedText>
        </div>
    </div>
</template>
