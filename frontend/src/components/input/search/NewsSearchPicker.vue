/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {listPublicBlog, searchNews, type NewsSearchResult} from '@/api/news'

const model = defineModel<string | null>()

const props = defineProps<{
    /** Public station UID used to resolve a stored {@code publicUid} back to its title. */
    stationUid?: string | null
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: NewsSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => searchNews(q, 5)
const displayFn = (item: NewsSearchResult) => item.title
const subtitleFn = (item: NewsSearchResult) => item.summary ?? ''
const keyFn = (item: NewsSearchResult) => item.publicUid
const iconFn = (): string[] => ['fas', 'newspaper']

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!props.stationUid || !model.value) { resolvedTitle.value = null; return }
    try {
        const entries = await listPublicBlog(props.stationUid, 0, 50)
        const match = entries.find(e => e.publicUid === model.value)
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
        :placeholder="placeholder ?? t('stationPages.editor.newsTeaserSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: NewsSearchResult) => emit('pick', it)"
    />
</template>
