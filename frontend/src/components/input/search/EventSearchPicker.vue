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
import {findEmbeddedEvent} from '@/components/content/blockeditor/embeddedEventLookup'
import {useEventEmbedScope} from '@/composables/useEventEmbedScope'
import {formatDateTime} from '@/util/format'

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

const scope = useEventEmbedScope()
const searchFn = (q: string) => searchEvents(q, props.mode, 10, scope)
const displayFn = (item: EventSearchResult) => item.name
const subtitleFn = (item: EventSearchResult) => {
    const parts = [
        formatDateTime(item.startTime),
        item.categoryName ?? '',
    ].filter(Boolean)
    return parts.join(' · ')
}
const keyFn = (item: EventSearchResult) => item.eventUid
const iconFn = (): string[] => ['fas', 'calendar-days']

const resolvedTitle = ref<string | null>(null)
async function resolve() {
    if (!props.stationUid || !model.value) { resolvedTitle.value = null; return }
    resolvedTitle.value = (await findEmbeddedEvent(props.stationUid, model.value))?.name ?? null
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
