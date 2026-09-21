/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import KbItemActions from './KbItemActions.vue'
import KbItemTileMedia from './KbItemTileMedia.vue'
import KbReachEye from './KbReachEye.vue'
import type {KbItem} from './useKbItems'

const props = defineProps<{
    item: KbItem
    /** Marking is on, and this entry is one of this station's own. */
    selectable?: boolean
    selected?: boolean
}>()

const emit = defineEmits<{
    toggleSelect: [key: string, value: boolean, shift: boolean]
}>()

function onCheckboxClick(event: MouseEvent) {
    emit('toggleSelect', props.item.key, !props.selected, event.shiftKey)
}
</script>

<template>
    <NeutralContainer
        data-testid="kb-item"
        class="hover:border-[var(--primary)] transition-colors relative group"
        :class="item.open ? 'cursor-pointer' : ''"
        @click="item.open?.()"
    >
        <KbReachEye v-if="item.shared && !selectable" :reach="item.shared" class="absolute top-1.5 left-1.5"/>

        <div
            v-if="selectable"
            class="absolute top-1 left-1 z-10 flex cursor-pointer select-none items-center rounded bg-(--bg)/90 p-1 backdrop-blur-sm"
            data-testid="kb-item-select"
            @click.stop.prevent="onCheckboxClick($event)"
        >
            <CheckboxInput :model-value="selected" class="pointer-events-none"/>
        </div>

        <div class="flex flex-col items-center gap-2 p-2 text-center">
            <KbItemTileMedia :item="item"/>

            <div class="flex items-center justify-center gap-1 w-full">
                <span class="text-sm font-medium truncate">{{ item.title }}</span>
                <font-awesome-icon
                    v-if="item.restricted"
                    :icon="['fas', 'lock']"
                    class="ml-1 h-3 w-3 text-[var(--text-muted)] flex-shrink-0"
                />
            </div>

            <span
                v-if="item.levelLabel"
                class="text-[10px] text-[var(--text-muted)] border border-[var(--border)] rounded-full px-2 py-0.5"
            >
                {{ item.levelLabel }}
            </span>

            <span v-if="item.countLabel" class="text-[10px] text-[var(--text-muted)]">{{ item.countLabel }}</span>
            <span v-if="item.description" class="text-xs text-[var(--text-muted)] truncate w-full">
                {{ item.description }}
            </span>
        </div>

        <div
            v-if="item.favourite || item.stationName"
            class="absolute top-1 left-1 flex max-w-[calc(100%-2.5rem)] items-center gap-1 overflow-hidden"
        >
            <font-awesome-icon v-if="item.favourite" :icon="['fas', 'star']" class="text-xs text-yellow-500"/>
            <StationBadge v-if="item.stationName" :station-name="item.stationName"/>
        </div>

        <div class="absolute top-1 right-1">
            <KbItemActions :actions="item.actions" hover-group/>
        </div>
    </NeutralContainer>
</template>
