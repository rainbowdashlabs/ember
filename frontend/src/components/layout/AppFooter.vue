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
import UpdateNotice from '@/components/layout/UpdateNotice.vue'
import client from '@/api/client'
import type {PublicConfigResponse} from '@/api/generated/schema'

/**
 * The footer of every page. The way to the discovery page shows at every width; what a layout puts
 * into the slot, a station or cluster switcher, shows on wider screens only, as it always has.
 */
const {t} = useI18n()
const version = ref('')

onMounted(async () => {
  const res = await client.get<PublicConfigResponse>('/public/config').catch(() => null)
  if (res) version.value = res.data.version
})
</script>

<template>
  <footer class="border-t border-bg-light-accent dark:border-bg-dark-accent py-6 px-4 mt-auto">
    <ClientOnly>
      <div class="max-w-6xl mx-auto mb-4 flex justify-center">
        <UpdateNotice/>
      </div>
    </ClientOnly>
    <div class="max-w-6xl mx-auto flex flex-col gap-6 md:grid md:grid-cols-3 md:gap-4">
      <div class="flex flex-col items-center md:items-start gap-2">
        <ClientOnly><ThemeToggle/></ClientOnly>
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

      <div class="flex flex-col items-center md:items-end gap-2">
        <router-link class="text-sm text-[var(--link)] hover:underline flex items-center gap-1" :to="{name: 'public-discovery'}" data-testid="footer-discovery">
          <font-awesome-icon :icon="['fas', 'compass']"/>
          {{ t('discovery.title') }}
        </router-link>
        <div class="hidden md:flex md:flex-col md:items-end gap-2">
          <slot/>
        </div>
      </div>
    </div>
  </footer>
</template>
