/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DarkMode } from '@/theme/themes'
import { useTheme } from '@/composables/useTheme'
import IconButton from '@/components/button/IconButton.vue'

const { t } = useI18n()
const { darkMode, setDarkMode } = useTheme()

const icon = computed(() => {
    switch (darkMode.value) {
        case DarkMode.SYSTEM:
            return 'circle-half-stroke'
        case DarkMode.LIGHT:
            return 'sun'
        case DarkMode.DARK:
            return 'moon'
        default:
            return 'circle-half-stroke'
    }
})

function cycle() {
    switch (darkMode.value) {
        case DarkMode.SYSTEM:
            setDarkMode(DarkMode.LIGHT)
            break
        case DarkMode.LIGHT:
            setDarkMode(DarkMode.DARK)
            break
        case DarkMode.DARK:
            setDarkMode(DarkMode.SYSTEM)
            break
    }
}
</script>

<template>
    <IconButton
        :icon="['fas', icon]"
        :label="t('theme.toggle')"
        class="text-[var(--text)] hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
        @click="cycle"
    >
        <font-awesome-icon :icon="['fas', icon]" class="h-5 w-5" />
    </IconButton>
</template>
