/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import NotFoundContent from './notfoundview/NotFoundContent.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import { emberLogo } from '@/composables/useEmberLogo'

const { t } = useI18n()
const logo = emberLogo()
</script>

<template>
  <div class="flex flex-col min-h-screen">
    <!-- Landing header -->
    <header class="border-b border-(--border) bg-(--bg)">
      <div class="mx-auto max-w-5xl px-6 py-4 flex items-center justify-between">
        <router-link to="/" class="flex items-center gap-2 no-underline">
          <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" :auto-blink="true" size="h-8 w-8" :pixel-size="64" />
          <span class="text-lg font-bold">Ember</span>
        </router-link>
        <div class="flex items-center gap-4 text-sm">
          <router-link to="/login" class="text-(--text-muted) hover:text-(--text) transition-colors no-underline">
            {{ t('landing.login') }}
          </router-link>
          <router-link to="/helpcenter/station/basics" class="text-(--text-muted) hover:text-(--text) transition-colors no-underline">
            {{ t('landing.helpCenter') }}
          </router-link>
        </div>
      </div>
    </header>

    <!-- 404 content -->
    <main class="flex-1 flex items-center justify-center">
      <NotFoundContent />
    </main>

    <!-- Footer -->
    <footer class="border-t border-(--border) bg-(--bg)">
      <div class="mx-auto max-w-5xl px-6 py-6 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-(--text-muted)">
        <span>{{ t('footer.copyright') }}</span>
        <div class="flex items-center gap-4">
          <router-link to="/privacy" class="hover:text-(--text) transition-colors no-underline text-(--text-muted)">{{ t('footer.privacy') }}</router-link>
          <router-link to="/terms" class="hover:text-(--text) transition-colors no-underline text-(--text-muted)">{{ t('footer.terms') }}</router-link>
          <router-link to="/imprint" class="hover:text-(--text) transition-colors no-underline text-(--text-muted)">{{ t('footer.imprint') }}</router-link>
        </div>
      </div>
    </footer>
  </div>
</template>
