/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import PartnerStationSearchPicker from '@/components/input/search/PartnerStationSearchPicker.vue'
import {resolvePartnerStations, type PublicPartnerSummary} from '@/api/publicPages'

const props = defineProps<{
    stationUid: string
    modelValue: string[]
}>()

const emit = defineEmits<{
    'update:modelValue': [value: string[]]
}>()

const {t} = useI18n()

const nameCache = ref<Record<string, string>>({})
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

const uids = computed(() => props.modelValue ?? [])

async function refreshNames(list: string[]) {
    const missing = list.filter(uid => !(uid in nameCache.value))
    if (missing.length === 0) return
    try {
        const resolved = await resolvePartnerStations(props.stationUid, missing)
        const next = {...nameCache.value}
        for (const r of resolved as PublicPartnerSummary[]) next[r.uid] = r.name
        nameCache.value = next
    } catch {
        // leave missing entries; UI falls back to the UUID.
    }
}

watch(uids, list => refreshNames(list), {immediate: true})

function displayName(uid: string): string {
    return nameCache.value[uid] ?? uid
}

function move(from: number, to: number) {
    if (from === to || from < 0 || to < 0 || from >= uids.value.length || to >= uids.value.length) return
    const next = [...uids.value]
    const [item] = next.splice(from, 1)
    next.splice(to, 0, item)
    emit('update:modelValue', next)
}

function remove(index: number) {
    emit('update:modelValue', uids.value.filter((_, i) => i !== index))
}

function add(stationUid: string) {
    if (uids.value.includes(stationUid)) return
    emit('update:modelValue', [...uids.value, stationUid])
}

function onDragStart(event: DragEvent, index: number) {
    dragIndex.value = index
    if (event.dataTransfer) {
        event.dataTransfer.effectAllowed = 'move'
        event.dataTransfer.setData('text/plain', String(index))
    }
}

function onDragOver(event: DragEvent, index: number) {
    if (dragIndex.value === null) return
    event.preventDefault()
    dragOverIndex.value = index
}

function onDrop(event: DragEvent, index: number) {
    event.preventDefault()
    if (dragIndex.value !== null) move(dragIndex.value, index)
    dragIndex.value = null
    dragOverIndex.value = null
}

function onDragEnd() {
    dragIndex.value = null
    dragOverIndex.value = null
}
</script>

<template>
    <ul class="space-y-1 mb-2 text-sm">
        <li
            v-for="(uid, i) in uids"
            :key="uid"
            draggable="true"
            class="flex items-center gap-2 px-2 py-1 rounded-theme border border-(--border) cursor-move transition-colors"
            :class="dragOverIndex === i && dragIndex !== i ? 'border-primary bg-primary/5' : ''"
            @dragstart="onDragStart($event, i)"
            @dragover="onDragOver($event, i)"
            @drop="onDrop($event, i)"
            @dragend="onDragEnd"
        >
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) shrink-0"/>
            <font-awesome-icon :icon="['fas', 'handshake']" class="text-primary shrink-0"/>
            <span class="flex-1 truncate" :title="uid">{{ displayName(uid) }}</span>
            <IconButton
                :icon="['fas', 'arrow-up']"
                :label="t('stationPages.editor.partnerStationMoveUp')"
                :disabled="i === 0"
                class="text-(--text-muted) hover:text-primary !p-1"
                @click="move(i, i - 1)"
            />
            <IconButton
                :icon="['fas', 'arrow-down']"
                :label="t('stationPages.editor.partnerStationMoveDown')"
                :disabled="i === uids.length - 1"
                class="text-(--text-muted) hover:text-primary !p-1"
                @click="move(i, i + 1)"
            />
            <IconButton
                :icon="['fas', 'xmark']"
                :label="t('stationPages.editor.removePartnerStation')"
                class="text-(--text-muted) hover:text-error !p-1"
                @click="remove(i)"
            />
        </li>
    </ul>
    <PartnerStationSearchPicker
        :model-value="null"
        :exclude-uids="uids"
        @pick="(item: {stationUid: string}) => add(item.stationUid)"
    />
</template>
