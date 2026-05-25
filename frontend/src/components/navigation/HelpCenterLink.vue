/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import EmberLogo from '@/components/display/EmberLogo.vue'

const route = useRoute()
const router = useRouter()
const {t} = useI18n()

/**
 * Explicit overrides for routes where the help page name differs from
 * the automatic `help-{routeName}` convention.
 */
const OVERRIDES: Record<string, string> = {
  'kb-browse': 'help-knowledge-base',
  'kb-file': 'help-knowledge-base',
  'kb-versions': 'help-knowledge-base',
  'quiz-catalog-generate': 'help-quiz-ai',
  'quiz-test-take': 'help-quiz-test-detail',
}

/**
 * Module overview fallbacks — when a route has no direct help page,
 * fall back to the module overview.
 */
const MODULE_FALLBACKS: Record<string, string> = {
  'dashboard': 'help-dashboard-module-overview',
  'news': 'help-news-module-overview',
  'profile': 'help-profile-module-overview',
  'station': 'help-manage-module-overview',
  'members': 'help-members-module-overview',
  'inventory': 'help-inventory-module-overview',
  'attendance': 'help-attendance-module-overview',
  'events': 'help-events-module-overview',
  'forms': 'help-forms-module-overview',
  'quiz': 'help-quiz-module-overview',
  'kb': 'help-knowledge-module-overview',
  'protocol': 'help-knowledge-module-overview',
  'admin': 'help-admin-module-overview',
}

function routeExists(name: string): boolean {
  return router.getRoutes().some(r => r.name === name)
}

const helpRoute = computed(() => {
  const name = route.name as string
  if (!name) return {name: 'help-dashboard-module-overview'}

  // 1. Check explicit overrides
  if (OVERRIDES[name]) return {name: OVERRIDES[name]}

  // 2. Try automatic convention: help-{routeName}
  const autoName = `help-${name}`
  if (routeExists(autoName)) return {name: autoName}

  // 3. Fall back to module overview based on route name prefix
  const prefix = name.split('-')[0]
  if (MODULE_FALLBACKS[prefix]) return {name: MODULE_FALLBACKS[prefix]}

  // 4. Default
  return {name: 'help-dashboard-module-overview'}
})
</script>

<template>
  <router-link
      :to="helpRoute"
      class="shrink-0 inline-flex items-center gap-1.5 text-sm text-[var(--text-muted)] hover:text-primary transition-colors"
      target="_blank"
  >
    <EmberLogo base="NoBG_NoGlow_FAQ" blink-base="NoBG_NoGlow_FAQ_Blink" :pixel-size="128" size="w-6 h-6 shrink-0" :blink="true" />
    <span class="hidden sm:inline">{{ t('helpCenter.link') }}</span>
  </router-link>
</template>
