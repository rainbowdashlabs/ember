/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'
import {searchFederationStations} from '@/api/federation'
import type {StationPickerResult} from '@/api/generated/schema'
import {placeLine} from '@/util/format'

const model = defineModel<string | null>()

const props = defineProps<{
    selectedDisplay?: string | null
    placeholder?: string
    disabled?: boolean
    excludeUids?: string[]
}>()

const emit = defineEmits<{
    pick: [item: StationPickerResult]
}>()

const {t} = useI18n()

const searchFn = async (q: string) => {
    const results = await searchFederationStations(q, 20)
    const exclude = new Set(props.excludeUids ?? [])
    return exclude.size === 0 ? results : results.filter(r => !exclude.has(r.stationUid))
}
const displayFn = (item: StationPickerResult) => item.name
const subtitleFn = (item: StationPickerResult) => placeLine(item)
const keyFn = (item: StationPickerResult) => item.stationUid
const iconFn = (): string[] => ['fas', 'handshake']
const isSelectableFn = (item: StationPickerResult) => item.selectable
</script>

<template>
    <EntitySearchPicker
        v-model="model"
        :search-fn="searchFn"
        :display-fn="displayFn"
        :subtitle-fn="subtitleFn"
        :key-fn="keyFn"
        :icon-fn="iconFn"
        :is-selectable-fn="isSelectableFn"
        :selected-display="selectedDisplay"
        :placeholder="placeholder ?? t('stationPages.editor.partnerStationSearchPlaceholder')"
        :not-selectable-hint="t('stationPages.editor.stationNotPublic')"
        :disabled="disabled"
        @pick="(it: StationPickerResult) => emit('pick', it)"
    />
</template>
