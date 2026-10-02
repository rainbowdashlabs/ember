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
 * Draws a layout cell on a page. Which component draws which type, and what it is handed, is
 * looked up in {@link LAYOUT_CELLS}; this component only binds it.
 */
const props = defineProps<{
    kind: LayoutKindName
    content: string
    config: Record<string, unknown>
    stationUid?: string
    /** The clock a cell writes a date on, which a public page names and a station page leaves out. */
    timezone?: string | null
}>()

const render = computed(() => LAYOUT_CELLS[props.kind].render)

const bound = computed(() => cellProps(render.value, props.config, {
    content: props.content,
    stationUid: props.stationUid,
    timezone: props.timezone,
    kind: props.kind,
}))
</script>

<template>
    <component :is="render.component" v-bind="bound"/>
</template>
