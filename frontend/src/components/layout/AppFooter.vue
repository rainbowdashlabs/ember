/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ThemeToggle from '@/components/theme/ThemeToggle.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import client from '@/api/client'

const {t} = useI18n()
const version = ref('')

onMounted(async () => {
  try {
    const res = await client.get<{ version?: string }>('/public/config')
    version.value = res.data.version ?? ''
  } catch { /* ignore */ }
})
</script>

<template>
  <footer class="border-t border-bg-light-accent dark:border-bg-dark-accent py-6 px-4 mt-auto">
    <div class="max-w-6xl mx-auto flex flex-col gap-6 md:grid md:grid-cols-3 md:gap-4">
      <div class="flex flex-col items-center md:items-start gap-2">
        <ThemeToggle/>
        <div class="flex flex-col items-center md:items-start gap-1 text-sm">
          <router-link class="text-[var(--link)] hover:underline" to="/privacy">{{ t('footer.privacy') }}</router-link>
          <router-link class="text-[var(--link)] hover:underline" to="/terms">{{ t('footer.terms') }}</router-link>
          <router-link class="text-[var(--link)] hover:underline" to="/imprint">{{ t('footer.imprint') }}</router-link>
        </div>
      </div>

      <div class="flex flex-col items-center gap-1 text-sm text-[var(--text-muted)] text-center">
        <AppLink :icon="['fab', 'github']" external href="https://github.com/rainbowdashlabs/ember">GitHub</AppLink>
        <span>{{ t('footer.copyright') }}</span>
        <span>{{ t('footer.madeWith') }}</span>
        <span>{{ t('footer.license') }}</span>
        <router-link v-if="version" to="/patch-notes" class="text-xs text-[var(--link)] hover:underline">
          Ember {{ version }}
        </router-link>
      </div>

      <div class="hidden md:flex md:flex-col md:items-end gap-2">
        <router-link class="text-sm text-[var(--link)] hover:underline flex items-center gap-1" :to="{name: 'public-discovery'}">
          <font-awesome-icon :icon="['fas', 'compass']"/>
          {{ t('discovery.title') }}
        </router-link>
        <slot/>
      </div>
    </div>
  </footer>
</template>
