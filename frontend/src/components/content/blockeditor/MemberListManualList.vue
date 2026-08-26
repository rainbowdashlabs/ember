/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DragList from '@/components/input/DragList.vue'

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

function memberLabel(uid: string): string {
    if (props.memberNames[uid]) return props.memberNames[uid]
    if (props.memberResolveAttempted[uid]) return t('stationPages.editor.memberListMissingMember')
    return uid
}
</script>

<template>
    <DragList
        :items="uids"
        :key-fn="(uid) => uid"
        class="mb-2 space-y-2 text-sm"
        @reorder="(from, to) => emit('move', from, to)"
    >
        <template #default="{item: uid}">
            <div class="space-y-2 px-2 py-2 rounded-theme border border-(--border)">
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
        </template>
    </DragList>
</template>
