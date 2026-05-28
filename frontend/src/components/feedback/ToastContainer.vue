/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useToast } from '@/composables/useToast'

const { toasts, dismiss } = useToast()
</script>

<template>
    <Teleport to="body">
        <div class="fixed top-4 right-4 z-[100] flex flex-col gap-2 max-w-sm">
            <TransitionGroup name="toast">
                <div
                    v-for="toast in toasts"
                    :key="toast.id"
                    class="flex items-start gap-3 rounded-theme border p-3 shadow-lg cursor-pointer"
                    :class="{
                        'border-info bg-info/10 dark:bg-info/20': toast.variant === 'info',
                        'border-success bg-success/10 dark:bg-success/20': toast.variant === 'success',
                        'border-error bg-error/10 dark:bg-error/20': toast.variant === 'error',
                    }"
                    style="backdrop-filter: blur(8px)"
                    @click="dismiss(toast.id)"
                >
                    <font-awesome-icon
                        :icon="['fas', toast.variant === 'success' ? 'circle-check' : toast.variant === 'error' ? 'triangle-exclamation' : 'circle-info']"
                        :class="{
                            'text-info-badge': toast.variant === 'info',
                            'text-success-badge': toast.variant === 'success',
                            'text-error-badge': toast.variant === 'error',
                        }"
                        class="mt-0.5 shrink-0"
                    />
                    <p class="text-sm text-(--text)">{{ toast.message }}</p>
                </div>
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
