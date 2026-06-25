/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import type {ResolvedMember} from '@/api/pageManage'

defineProps<{
    members: ResolvedMember[]
    descriptionFor: (uid: string) => string
}>()

defineEmits<{
    remove: [string]
    setDescription: [string, string | undefined]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)
</script>

<template>
    <ul class="space-y-2 mb-2 text-sm">
        <li
            v-for="m in members"
            :key="m.memberUid"
            class="flex items-stretch gap-2 px-2 py-2 rounded-theme border border-(--border)"
        >
            <div class="flex-1 min-w-0 space-y-2">
                <div class="flex items-center gap-2">
                    <font-awesome-icon :icon="['fas', 'user']" class="text-primary shrink-0"/>
                    <span class="flex-1 truncate" :title="m.memberUid">{{ m.displayName }}</span>
                    <MutedIconButton
                        :icon="['fas', 'xmark']"
                        :label="TS('memberListManualRemove')"
                        hover="error"
                        class="p-1!"
                        @click="$emit('remove', m.memberUid)"
                    />
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
