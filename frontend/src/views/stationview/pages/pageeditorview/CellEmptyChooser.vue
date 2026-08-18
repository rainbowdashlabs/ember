/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import BaseButton from '@/components/button/BaseButton.vue'
import {usePageClipboard} from '@/composables/usePageClipboard'
import {CHOOSER_CATEGORIES} from './cellChoosers'

/**
 * Picker shown inside an EMPTY cell. Offers a category-grouped grid of content-type buttons plus
 * a paste-here shortcut when the clipboard holds a cell. Owns its own search filter - the parent
 * just listens for the chosen content type via {@code pick} or applies a clipboard paste via
 * {@code paste-here}.
 */
const emit = defineEmits<{
    pick: [type: string]
    'paste-here': []
}>()

const {t} = useI18n()
const {hasClipboard, clipboardType} = usePageClipboard()

const search = ref('')
const filteredCategories = computed(() => {
    const q = search.value.trim().toLowerCase()
    if (!q) return CHOOSER_CATEGORIES.map(c => ({...c, items: [...c.items]}))
    return CHOOSER_CATEGORIES
        .map(c => ({
            ...c,
            items: c.items.filter(i => t(`stationPages.editor.${i.key}`).toLowerCase().includes(q)),
        }))
        .filter(c => c.items.length > 0)
})
</script>

<template>
    <div class="@container flex flex-col gap-3 py-4 px-3 border-2 border-dashed border-(--border) rounded-theme">
        <p class="text-sm text-(--text-muted) text-center">{{ t('stationPages.editor.emptyCellHint') }}</p>
        <SearchInput v-model="search" :placeholder="t('stationPages.editor.chooserSearch')" class="w-full"/>
        <div class="max-h-96 overflow-y-auto -mx-1 px-1 space-y-3">
            <BaseButton
                v-if="hasClipboard && clipboardType === 'cell'"
                full-width
                class="gap-2 !px-3 !py-2 !font-normal border border-primary/40 hover:border-primary hover:bg-primary/5 text-primary"
                @click="emit('paste-here')"
            >
                <font-awesome-icon :icon="['fas', 'paste']"/>
                {{ t('stationPages.editor.pasteCell') }}
            </BaseButton>
            <div v-for="cat in filteredCategories" :key="cat.key" class="space-y-1">
                <p class="text-[10px] uppercase tracking-wider text-(--text-muted) font-semibold">
                    {{ t(`stationPages.editor.${cat.key}`) }}
                </p>
                <div class="grid grid-cols-1 @[10rem]:grid-cols-2 @md:grid-cols-3 @xl:grid-cols-4 gap-2">
                    <BaseButton
                        v-for="entry in cat.items" :key="entry.type"
                        class="flex-col gap-1 !px-3 !py-3 !font-normal border border-(--border) hover:border-primary hover:bg-primary/5"
                        @click="emit('pick', entry.type)"
                    >
                        <font-awesome-icon :icon="['fas', entry.icon]" class="text-lg text-primary"/>
                        <span class="text-xs text-center">{{ t(`stationPages.editor.${entry.key}`) }}</span>
                    </BaseButton>
                </div>
            </div>
        </div>
    </div>
</template>
