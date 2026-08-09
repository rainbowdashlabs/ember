/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import * as publicKb from '@/api/publicKb'
import type {KbFile} from '@/api/knowledgeBase'

/**
 * Picker for WIKI_ARTICLE cells. Backed by the existing public KB search endpoint so it works
 * on stations where the wiki is public. The cell config stores the KB file id (int) as a string
 * for compatibility with {@link EntitySearchPicker}'s {@code string | null} model.
 */
const model = defineModel<string | null>()

const props = defineProps<{
    stationUid?: string | null
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>()

const emit = defineEmits<{
    pick: [item: KbFile]
}>()

const {t} = useI18n()

const searchFn = async (q: string): Promise<KbFile[]> => {
    if (!props.stationUid) return []
    if (!q || !q.trim()) {
        const browse = await publicKb.browse(props.stationUid).catch(() => null)
        return browse?.files ?? []
    }
    const results = await publicKb.search(props.stationUid, q).catch(() => [])
    return results.map(r => r.file)
}
const displayFn = (item: KbFile) => item.name
const subtitleFn = (item: KbFile) => item.description ?? ''
const keyFn = (item: KbFile) => String(item.id)
const iconFn = (): string[] => ['fas', 'book']

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!props.stationUid || !model.value) { resolvedTitle.value = null; return }
    const id = Number(model.value)
    if (!Number.isFinite(id)) { resolvedTitle.value = null; return }
    try {
        const file = await publicKb.getFile(props.stationUid, id)
        resolvedTitle.value = file?.name ?? null
    } catch { resolvedTitle.value = null }
}
onMounted(resolve)
watch(() => [props.stationUid, model.value], resolve)

const idModel = computed<string | null>({
    get: () => model.value ?? null,
    set: v => { model.value = v },
})
</script>

<template>
    <EntitySearchPicker
        v-model="idModel"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :selected-display="resolvedTitle ?? selectedDisplay"
        :placeholder="placeholder ?? t('stationPages.editor.wikiSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: KbFile) => emit('pick', it)"
    />
</template>
