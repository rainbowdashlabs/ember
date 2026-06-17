/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {searchPages, type PageSearchResult} from '@/api/pageManage'
import {listPublicPages} from '@/api/publicPages'

const model = defineModel<string | null>()

const props = defineProps<{
    /** Public station UID used to resolve a stored {@code pageUid} back to its title. */
    stationUid?: string | null
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: PageSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => searchPages(q, 5)
const displayFn = (item: PageSearchResult) => item.title
const subtitleFn = (item: PageSearchResult) => `/${item.slug}`
const keyFn = (item: PageSearchResult) => item.pageUid
const iconFn = (): string[] => ['fas', 'file-lines']

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!props.stationUid || !model.value) { resolvedTitle.value = null; return }
    try {
        const pages = await listPublicPages(props.stationUid)
        const match = pages.find(p => p.publicUid === model.value)
        resolvedTitle.value = match?.title ?? null
    } catch { resolvedTitle.value = null }
}
onMounted(resolve)
watch(() => [props.stationUid, model.value], resolve)
</script>

<template>
    <EntitySearchPicker
        v-model="model"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :selected-display="resolvedTitle ?? selectedDisplay"
        :placeholder="placeholder ?? t('stationPages.editor.pageSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: PageSearchResult) => emit('pick', it)"
    />
</template>
