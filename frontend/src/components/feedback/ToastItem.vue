/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import {dismissToast, holdToast, releaseToast, type Toast, type ToastAction} from '@/util/toast'

/**
 * One toast: its message, the action it may offer and the button that closes it.
 *
 * <p>Its clock stops while the pointer rests on it or the focus is inside it, so it cannot vanish
 * under a reader who is still reading it or about to press its action. Holding it is all its
 * pointer and focus listeners do, so the box itself is presentation: the live region around it
 * announces the message, and its buttons are the controls.
 */
const props = defineProps<{
    toast: Toast
}>()

const {t} = useI18n()

const ICONS: Record<Toast['variant'], string> = {
    info: 'circle-info',
    success: 'circle-check',
    error: 'triangle-exclamation',
}

/**
 * Carries out what the toast offered and closes it. The toast goes first: whatever it was offering is
 * now happening, and leaving it on screen would invite a second press of the same undo.
 */
async function runAction(action: ToastAction) {
    dismissToast(props.toast.id)
    await action.run()
}

/** Lets go of the focus hold only once the focus has left the toast, not when it moves within it. */
function onFocusOut(event: FocusEvent) {
    const next = event.relatedTarget
    if (next instanceof Node && (event.currentTarget as HTMLElement).contains(next)) return
    releaseToast(props.toast.id, 'focus')
}
</script>

<template>
    <div
        :class="{
            'border-info bg-info/10 dark:bg-info/20': toast.variant === 'info',
            'border-success bg-success/10 dark:bg-success/20': toast.variant === 'success',
            'border-error bg-error/10 dark:bg-error/20': toast.variant === 'error',
        }"
        class="pointer-events-auto flex items-start gap-3 rounded-theme border p-3 shadow-lg"
        data-testid="toast"
        role="presentation"
        style="backdrop-filter: blur(8px)"
        @focusin="holdToast(toast.id, 'focus')"
        @focusout="onFocusOut"
        @mouseenter="holdToast(toast.id, 'pointer')"
        @mouseleave="releaseToast(toast.id, 'pointer')"
    >
        <font-awesome-icon
            :class="{
                'text-info-badge': toast.variant === 'info',
                'text-success-badge': toast.variant === 'success',
                'text-error-badge': toast.variant === 'error',
            }"
            :icon="['fas', ICONS[toast.variant]]"
            class="mt-0.5 shrink-0"
        />
        <p class="text-sm text-(--text) grow">{{ toast.message }}</p>
        <button
            v-if="toast.action"
            class="shrink-0 text-sm font-medium underline text-(--text) hover:no-underline"
            data-testid="toast-action"
            type="button"
            @click="runAction(toast.action)"
        >{{ toast.action.label }}</button>
        <IconButton
            :icon="['fas', 'xmark']"
            :label="t('toast.dismiss')"
            class="-my-1.5 shrink-0 text-(--text-muted) hover:text-(--text)"
            data-testid="toast-dismiss"
            @click="dismissToast(toast.id)"
        />
    </div>
</template>
