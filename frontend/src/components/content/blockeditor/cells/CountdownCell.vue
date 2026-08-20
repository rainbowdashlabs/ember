/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, onUnmounted, ref} from 'vue'
import type {CountdownConfig} from '@/api/pageManage'

const props = defineProps<{
    config: CountdownConfig
}>()

const state = ref({days: 0, hours: 0, minutes: 0, seconds: 0, expired: true})
let timer: ReturnType<typeof setInterval> | null = null

function tick() {
    const target = props.config.targetDate ? new Date(props.config.targetDate).getTime() : 0
    const diff = target - Date.now()
    if (diff <= 0) {
        state.value = {days: 0, hours: 0, minutes: 0, seconds: 0, expired: true}
        return
    }
    state.value = {
        days: Math.floor(diff / 86_400_000),
        hours: Math.floor((diff % 86_400_000) / 3_600_000),
        minutes: Math.floor((diff % 3_600_000) / 60_000),
        seconds: Math.floor((diff % 60_000) / 1000),
        expired: false,
    }
}

onMounted(() => {
    tick()
    timer = setInterval(tick, 1000)
})
onUnmounted(() => {
    if (timer) clearInterval(timer)
})
</script>

<template>
    <div class="rounded-theme border border-(--border) bg-bg-light-accent/30 dark:bg-bg-dark-accent/20 p-4 text-center space-y-2">
        <p v-if="config.label" class="text-sm font-semibold">{{ config.label }}</p>
        <div v-if="!state.expired" class="flex justify-center gap-3 sm:gap-6">
            <div v-for="part in [
                {n: state.days, l: 'Tage'},
                {n: state.hours, l: 'Std'},
                {n: state.minutes, l: 'Min'},
                {n: state.seconds, l: 'Sek'},
            ]" :key="part.l" class="text-center">
                <div class="text-3xl font-bold text-primary tabular-nums">{{ part.n }}</div>
                <div class="text-xs text-(--text-muted) uppercase">{{ part.l }}</div>
            </div>
        </div>
        <p v-else class="text-lg font-semibold text-primary">⏰</p>
        <p v-if="config.sublabel" class="text-xs text-(--text-muted)">{{ config.sublabel }}</p>
    </div>
</template>
