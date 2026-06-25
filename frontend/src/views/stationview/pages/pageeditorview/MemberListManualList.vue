/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import MemberListReorderControls from './MemberListReorderControls.vue'

const props = defineProps<{
    uids: string[]
    memberNames: Record<string, string>
    memberResolveAttempted: Record<string, boolean>
    descriptionFor: (uid: string) => string
}>()

const emit = defineEmits<{
    move: [number, number]
    remove: [string]
    setDescription: [string, string | undefined]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)

const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function memberLabel(uid: string): string {
    if (props.memberNames[uid]) return props.memberNames[uid]
    if (props.memberResolveAttempted[uid]) return t('stationPages.editor.memberListMissingMember')
    return uid
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
            v-for="(uid, i) in uids"
            :key="uid"
            :draggable="true"
            class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border) cursor-move transition-colors"
            :class="dragOverIndex === i && dragIndex !== i ? 'border-primary bg-primary/5' : ''"
            @dragstart="onDragStart($event, i)"
            @dragover="onDragOver($event, i)"
            @drop="onDrop($event, i)"
            @dragend="onDragEnd"
        >
            <MemberListReorderControls
                :index="i"
                :total="uids.length"
                @move="(from: number, to: number) => $emit('move', from, to)"
            />
            <div class="flex-1 min-w-0 space-y-2">
                <div class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                    <span
                        class="flex-1 truncate"
                        :title="uid"
                        :class="memberResolveAttempted[uid] && !memberNames[uid] ? 'italic text-(--text-muted)' : ''"
                    >{{ memberLabel(uid) }}</span>
                    <MutedIconButton
                        :icon="['fas', 'xmark']"
                        :label="TS('memberListManualRemove')"
                        hover="error"
                        class="p-1!"
                        @click="$emit('remove', uid)"
                    />
                </div>
                <TextInput
                    :model-value="descriptionFor(uid)"
                    :placeholder="TS('memberListDescriptionPlaceholder')"
                    @update:model-value="(v: string | undefined) => $emit('setDescription', uid, v)"
                />
            </div>
        </li>
    </ul>
</template>
