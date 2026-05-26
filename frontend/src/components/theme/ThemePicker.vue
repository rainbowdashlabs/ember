/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { THEMES } from '@/theme/themes'
import { useTheme } from '@/composables/useTheme'

const { t } = useI18n()
const { activeTheme, customThemeColors, setTheme } = useTheme()

const themeEntries = computed(() => {
    const entries = Object.entries(THEMES).map(([key, theme]) => ({
        key,
        label: theme.label,
        colors: theme.colors,
    }))
    if (customThemeColors.value) {
        entries.push({
            key: 'custom',
            label: t('theme.custom'),
            colors: customThemeColors.value,
        })
    }
    return entries
})
</script>

<template>
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-3">
        <button
            v-for="entry in themeEntries"
            :key="entry.key"
            class="flex flex-col items-center gap-2 rounded-theme border-2 p-3 transition-all duration-150
                   bg-[var(--bg)] hover:shadow-md"
            :class="activeTheme === entry.key
                ? 'border-[var(--color-primary)] ring-2 ring-[var(--color-primary)]'
                : 'border-[var(--border)] hover:border-[var(--color-primary)]/50'"
            @click="setTheme(entry.key)"
        >
            <div class="flex gap-1.5">
                <span
                    class="h-5 w-5 rounded-full border border-black/10"
                    :style="{ backgroundColor: entry.colors.primary }"
                />
                <span
                    class="h-5 w-5 rounded-full border border-black/10"
                    :style="{ backgroundColor: entry.colors.secondary }"
                />
                <span
                    class="h-5 w-5 rounded-full border border-black/10"
                    :style="{ backgroundColor: entry.colors.success }"
                />
                <span
                    class="h-5 w-5 rounded-full border border-black/10"
                    :style="{ backgroundColor: entry.colors.error }"
                />
            </div>
            <span class="text-sm font-medium text-[var(--text)]">{{ entry.label }}</span>
        </button>
    </div>
</template>
