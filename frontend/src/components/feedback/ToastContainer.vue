/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {getToasts} from '@/util/toast'
import ToastItem from './ToastItem.vue'

/**
 * The toasts, in two live regions that stand in the page before any toast arrives, which is what
 * lets a screen reader announce one: errors in an `alert` region that interrupts, everything else
 * in a polite `status` region that waits its turn.
 */
const toasts = getToasts()

const errors = computed(() => toasts.value.filter(toast => toast.variant === 'error'))

const notices = computed(() => toasts.value.filter(toast => toast.variant !== 'error'))
</script>

<template>
    <Teleport to="body">
        <div class="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
            <TransitionGroup aria-atomic="false" class="flex flex-col gap-2" data-testid="toast-alerts" name="toast" role="alert" tag="div">
                <ToastItem v-for="toast in errors" :key="toast.id" :toast="toast"/>
            </TransitionGroup>
            <TransitionGroup aria-atomic="false" aria-live="polite" class="flex flex-col gap-2" data-testid="toast-notices" name="toast" role="status" tag="div">
                <ToastItem v-for="toast in notices" :key="toast.id" :toast="toast"/>
            </TransitionGroup>
        </div>
    </Teleport>
</template>

<style scoped>
.toast-enter-active {
    transition: all 0.3s ease;
}
.toast-leave-active {
    transition: all 0.2s ease;
}
.toast-enter-from {
    opacity: 0;
    transform: translateX(100%);
}
.toast-leave-to {
    opacity: 0;
    transform: translateX(100%);
}
</style>
