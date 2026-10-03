/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'

const {t} = useI18n()

const emit = defineEmits<{
    'update:modelValue': [value: File | null]
}>()

const inputId = useId()

function onSelect(event: Event) {
    const input = event.target as HTMLInputElement
    emit('update:modelValue', input.files?.[0] ?? null)
}
</script>

<template>
    <div class="space-y-2">
        <label :for="inputId" class="text-xs text-[var(--text-muted)]">{{ t('kb.folderIcon') }}</label>
        <input
            :id="inputId"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            class="block w-full text-sm text-[var(--text)] file:mr-4 file:py-1 file:px-3 file:rounded file:border-0 file:text-xs file:font-semibold file:bg-[var(--color-primary)] file:text-[var(--color-primary-text)] cursor-pointer"
            @change="onSelect"
        />
    </div>
</template>
