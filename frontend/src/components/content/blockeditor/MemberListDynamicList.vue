/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MemberListReorderControls from './MemberListReorderControls.vue'
import type {ResolvedMember} from '@/api/pageManage'

const props = defineProps<{
    members: ResolvedMember[]
    isOrderSort: boolean
    descriptionFor: (uid: string) => string
}>()

const emit = defineEmits<{
    move: [number, number]
    setDescription: [string, string | undefined]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

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
    if (dragIndex.value !== null) emit('move', dragIndex.value, index)
    dragIndex.value = null
    dragOverIndex.value = null
}
function onDragEnd() {
    dragIndex.value = null
    dragOverIndex.value = null
}
</script>

<template>
    <ul class="space-y-2 mb-2 text-sm">
        <li
            v-for="(m, i) in members"
            :key="m.memberUid"
            :draggable="isOrderSort"
            class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border) transition-colors"
            :class="[
                isOrderSort ? 'cursor-move' : '',
                isOrderSort && dragOverIndex === i && dragIndex !== i ? 'border-primary bg-primary/5' : '',
            ]"
            @dragstart="isOrderSort ? onDragStart($event, i) : undefined"
            @dragover="isOrderSort ? onDragOver($event, i) : undefined"
            @drop="isOrderSort ? onDrop($event, i) : undefined"
            @dragend="onDragEnd"
        >
            <MemberListReorderControls
                v-if="isOrderSort"
                :index="i"
                :total="members.length"
                @move="(from: number, to: number) => $emit('move', from, to)"
            />
            <div class="flex-1 min-w-0 space-y-2">
                <div class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                    <span class="flex-1 truncate" :title="m.memberUid">{{ m.displayName }}</span>
                </div>
                <TextInput
                    :model-value="descriptionFor(m.memberUid)"
                    :placeholder="TS('memberListDescriptionPlaceholder')"
                    @update:model-value="(v: string | undefined) => $emit('setDescription', m.memberUid, v)"
                />
            </div>
        </li>
    </ul>
</template>
