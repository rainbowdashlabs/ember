/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'

function getInitialTheme(): boolean {
  const stored = localStorage.getItem('theme')
  if (stored !== null) {
    return stored === 'dark'
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches
}

const isDark = ref(getInitialTheme())
applyTheme(isDark.value)

function applyTheme(dark: boolean) {
  const root = document.documentElement
  root.classList.toggle('dark', dark)
  root.classList.toggle('light', !dark)
  localStorage.setItem('theme', dark ? 'dark' : 'light')
}

function toggle() {
  isDark.value = !isDark.value
  applyTheme(isDark.value)
}
</script>

<template>
  <button
      aria-label="Toggle theme"
      class="p-2 rounded-lg text-[var(--text)] transition-colors duration-150 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
      @click="toggle"
  >
    <font-awesome-icon :icon="['fas', isDark ? 'sun' : 'moon']" class="h-5 w-5"/>
  </button>
</template>
