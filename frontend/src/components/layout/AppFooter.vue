/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ThemeToggle from '@/components/theme/ThemeToggle.vue'
import AppLink from '@/components/navigation/AppLink.vue'

const {t} = useI18n()
</script>

<template>
  <footer class="border-t border-bg-light-accent dark:border-bg-dark-accent py-6 px-4 mt-auto">
    <div class="max-w-6xl mx-auto flex flex-col gap-6 md:grid md:grid-cols-3 md:gap-4">
      <div class="flex flex-col items-center md:items-start gap-2">
        <ThemeToggle/>
        <div class="flex flex-col items-center md:items-start gap-1 text-sm">
          <AppLink href="/privacy">{{ t('footer.privacy') }}</AppLink>
          <AppLink href="/terms">{{ t('footer.terms') }}</AppLink>
        </div>
      </div>

      <div class="flex flex-col items-center gap-1 text-sm text-[var(--text-muted)] text-center">
        <AppLink :icon="['fab', 'github']" external href="https://github.com/rainbowdashlabs">GitHub</AppLink>
        <span>{{ t('footer.copyright') }}</span>
        <span>{{ t('footer.madeWith') }}</span>
        <span>{{ t('footer.license') }}</span>
      </div>

      <div class="hidden md:flex md:flex-col md:items-end">
        <slot/>
      </div>
    </div>
  </footer>
</template>
