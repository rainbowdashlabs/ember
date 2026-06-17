/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {searchEvents, type EventPickerMode, type EventSearchResult} from '@/api/events'
import {listPublicEvents} from '@/api/publicEvents'

const model = defineModel<string | null>()

const props = withDefaults(defineProps<{
    /** Time-window filter: 'FUTURE' (default), 'PAST' for recap cells, 'ALL'. */
    mode?: EventPickerMode
    /** Public station UID used to resolve a stored {@code eventUid} back to its title. */
    stationUid?: string | null
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
}>(), {
    mode: 'FUTURE',
})

const emit = defineEmits<{
    pick: [item: EventSearchResult]
}>()

const {t} = useI18n()

const searchFn = (q: string) => searchEvents(q, props.mode, 10)
const displayFn = (item: EventSearchResult) => item.name
const subtitleFn = (item: EventSearchResult) => {
    const parts = [
        item.startTime ? new Date(item.startTime).toLocaleString('de-DE', {dateStyle: 'medium', timeStyle: 'short'}) : '',
        item.categoryName ?? '',
    ].filter(Boolean)
    return parts.join(' · ')
}
const keyFn = (item: EventSearchResult) => item.eventUid
const iconFn = (): string[] => ['fas', 'calendar-days']

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!props.stationUid || !model.value) { resolvedTitle.value = null; return }
    try {
        const events = await listPublicEvents(props.stationUid)
        const match = events.find(e => e.publicUid === model.value)
        resolvedTitle.value = match?.name ?? null
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
        :placeholder="placeholder ?? t('stationPages.editor.eventSearchPlaceholder')"
        :disabled="disabled"
        @pick="(it: EventSearchResult) => emit('pick', it)"
    />
</template>
