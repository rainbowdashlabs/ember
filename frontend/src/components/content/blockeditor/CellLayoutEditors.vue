/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {LayoutKindName} from '@/api/pageManage'
import {cellProps, LAYOUT_CELLS} from './cellRegistry'

/**
 * The configuration panel of a layout cell in the page editor. Which editor configures which type,
 * and what it is handed, is looked up in {@link LAYOUT_CELLS}; this component only binds it and
 * passes its `update:config` and, where the editor owns a text body, `update:content` back up.
 */
const content = defineModel<string>('content', {required: true})
const config = defineModel<Record<string, unknown>>('config', {required: true})

const props = defineProps<{
    kind: LayoutKindName
    stationUid: string
}>()

const editor = computed(() => LAYOUT_CELLS[props.kind].editor)

const bound = computed(() => {
    const bindings = cellProps(editor.value, config.value, {
        content: content.value,
        stationUid: props.stationUid,
        kind: props.kind,
    })
    bindings['onUpdate:config'] = (value: Record<string, unknown>) => { config.value = value }
    if (editor.value.takes.includes('content')) {
        bindings['onUpdate:content'] = (value: string) => { content.value = value }
    }
    return bindings
})
</script>

<template>
    <div class="space-y-3">
        <component :is="editor.component" v-bind="bound"/>
    </div>
</template>
