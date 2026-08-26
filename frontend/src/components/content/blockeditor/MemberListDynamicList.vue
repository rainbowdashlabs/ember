/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import DragList from '@/components/input/DragList.vue'
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

</script>

<template>
    <DragList
        :items="members"
        :key-fn="(m) => m.memberUid"
        :disabled="!isOrderSort"
        class="mb-2 space-y-2 text-sm"
        @reorder="(from, to) => emit('move', from, to)"
    >
        <template #default="{item: m}">
            <div class="space-y-2 px-2 py-2 rounded-theme border border-(--border)">
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
        </template>
    </DragList>
</template>
