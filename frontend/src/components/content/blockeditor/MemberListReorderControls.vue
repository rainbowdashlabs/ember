/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

defineProps<{
    index: number
    total: number
}>()

defineEmits<{
    move: [number, number]
}>()

const {t} = useI18n()
const TS = (k: string) => t(`stationPages.editor.${k}`)
</script>

<template>
    <div class="flex flex-col items-center justify-between shrink-0">
        <MutedIconButton
            :icon="['fas', 'arrow-up']"
            :label="TS('memberListManualMoveUp')"
            :disabled="index === 0"
            class="p-0.5!"
            @click="$emit('move', index, index - 1)"
        />
        <font-awesome-icon
            :icon="['fas', 'grip-vertical']"
            class="text-(--text-muted)"
            :title="TS('memberListManualDragHandle')"
        />
        <MutedIconButton
            :icon="['fas', 'arrow-down']"
            :label="TS('memberListManualMoveDown')"
            :disabled="index === total - 1"
            class="p-0.5!"
            @click="$emit('move', index, index + 1)"
        />
    </div>
</template>
