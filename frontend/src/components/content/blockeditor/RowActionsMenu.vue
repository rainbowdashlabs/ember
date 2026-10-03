/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useEditorActionsMenu} from './useEditorActionsMenu'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'

defineProps<{
    isFirst?: boolean
    isLast?: boolean
    canPasteCell?: boolean
    label?: string
    /** Whether the row draws lines between its columns, or undefined where the editor offers none. */
    columnLines?: boolean
}>()

const emit = defineEmits<{
    copy: []
    cut: []
    delete: []
    'move-up': []
    'move-down': []
    'paste-cell': []
    'toggle-lines': []
}>()

const {t} = useI18n()

const {open, triggerVisibility, toggle, close} = useEditorActionsMenu()

defineExpose({close})
</script>

<template>
    <FloatingPanel v-model:open="open" :label="t('stationPages.editor.rowMenu')" role="menu" class="absolute top-1 right-1 z-10">
        <template #trigger="{triggerAttrs}">
            <IconButton
                :icon="['fas', 'ellipsis']"
                :label="t('stationPages.editor.rowMenu')"
                class="w-7 h-7 !p-0 bg-(--bg)/80 backdrop-blur-sm border border-(--border) text-(--text-muted) hover:text-(--text) hover:border-primary shadow-sm"
                :class="triggerVisibility"
                v-bind="triggerAttrs"
                @click="toggle"
            />
        </template>
        <p v-if="label" class="px-3 py-1 text-[10px] uppercase tracking-wider text-(--text-muted)">{{ label }}</p>
        <DropdownMenuItem
            v-if="!isFirst"
            :icon="['fas', 'angle-up']"
            @click="emit('move-up'); close()"
        >{{ t('common.moveUp') }}</DropdownMenuItem>
        <DropdownMenuItem
            v-if="!isLast"
            :icon="['fas', 'angle-down']"
            @click="emit('move-down'); close()"
        >{{ t('common.moveDown') }}</DropdownMenuItem>
        <div v-if="!isFirst || !isLast" class="border-t border-(--border) my-1"/>
        <DropdownMenuItem :icon="['fas', 'copy']" @click="emit('copy'); close()">{{ t('stationPages.editor.copyRow') }}</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'scissors']" @click="emit('cut'); close()">{{ t('stationPages.editor.cutRow') }}</DropdownMenuItem>
        <DropdownMenuItem
            v-if="canPasteCell"
            :icon="['fas', 'paste']"
            class="text-primary"
            @click="emit('paste-cell'); close()"
        >{{ t('stationPages.editor.pasteCell') }}</DropdownMenuItem>
        <DropdownMenuItem
            v-if="columnLines !== undefined"
            :icon="['fas', columnLines ? 'check' : 'grip-vertical']"
            data-testid="row-column-lines"
            @click="emit('toggle-lines'); close()"
        >{{ t('stationPages.editor.columnLines') }}</DropdownMenuItem>
        <div class="border-t border-(--border) my-1"/>
        <DropdownMenuItem :icon="['fas', 'trash']" class="text-error" @click="emit('delete'); close()">{{ t('common.delete') }}</DropdownMenuItem>
    </FloatingPanel>
</template>
