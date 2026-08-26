/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DragList from '@/components/input/DragList.vue'
import PartnerStationSearchPicker from '@/components/input/search/PartnerStationSearchPicker.vue'
import {resolvePartnerStations, type PublicPartnerSummary} from '@/api/publicPages'
import {moveWithin} from '@/util/reorder'

const modelValue = defineModel<string[]>({required: true})

const props = defineProps<{
    stationUid: string
}>()

const {t} = useI18n()

const nameCache = ref<Record<string, string>>({})

const uids = computed(() => modelValue.value ?? [])

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
    modelValue.value = moveWithin(uids.value, from, to)
}

function remove(index: number) {
    modelValue.value = uids.value.filter((_, i) => i !== index)
}

function add(stationUid: string) {
    if (uids.value.includes(stationUid)) return
    modelValue.value = [...uids.value, stationUid]
}

</script>

<template>
    <DragList :items="uids" :key-fn="(uid) => uid" class="mb-2 space-y-1 text-sm" @reorder="move">
        <template #default="{item: uid, index}">
            <div class="flex items-center gap-2 px-2 py-1 rounded-theme border border-(--border)">
                <font-awesome-icon :icon="['fas', 'handshake']" class="text-primary shrink-0"/>
                <span class="flex-1 truncate" :title="uid">{{ displayName(uid) }}</span>
                <MutedIconButton
                    :icon="['fas', 'xmark']"
                    :label="t('stationPages.editor.removePartnerStation')"
                    hover="error"
                    class="!p-1"
                    @click="remove(index)"
                />
            </div>
        </template>
    </DragList>
    <PartnerStationSearchPicker
        :model-value="null"
        :exclude-uids="uids"
        @pick="(item: {stationUid: string}) => add(item.stationUid)"
    />
</template>
