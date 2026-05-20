/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import client from '@/api/client'

const {t} = useI18n()
const demoUrl = ref('')

onMounted(async () => {
  try {
    const res = await client.get<{ demoUrl?: string }>('/public/config')
    demoUrl.value = res.data.demoUrl ?? ''
  } catch { /* ignore */ }

  // Inject JSON-LD structured data
  const script = document.createElement('script')
  script.type = 'application/ld+json'
  script.textContent = JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'Ember',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    description: t('landing.heroSubtitle'),
    offers: {
      '@type': 'Offer',
      price: '0',
      priceCurrency: 'EUR',
    },
    author: {
      '@type': 'Organization',
      name: 'RainbowDashLabs',
    },
  })
  document.head.appendChild(script)
})

const features = [
  {icon: ['fas', 'clipboard-user'], key: 'attendance', help: '/helpcenter/station/attendance/new'},
  {icon: ['fas', 'calendar-days'], key: 'events', help: '/helpcenter/station/events'},
  {icon: ['fas', 'boxes-stacked'], key: 'inventory', help: '/helpcenter/station/inventory/overview'},
  {icon: ['fas', 'users'], key: 'members', help: '/helpcenter/station/members/list'},
  {icon: ['fas', 'square-poll-vertical'], key: 'forms', help: '/helpcenter/station/forms'},
  {icon: ['fas', 'newspaper'], key: 'news', help: '/helpcenter/station/news'},
  {icon: ['fas', 'box-open'], key: 'lostAndFound', help: '/helpcenter/station/inventory/overview'},
  {icon: ['fas', 'bell'], key: 'notifications', help: '/helpcenter/station/profile/settings'},
  {icon: ['fas', 'circle-question'], key: 'helpCenter', help: '/helpcenter/station/basics'},
]

const highlights = [
  {icon: ['fas', 'mobile-screen'], key: 'responsive'},
  {icon: ['fas', 'moon'], key: 'darkMode'},
  {icon: ['fas', 'lock'], key: 'secure'},
  {icon: ['fas', 'users-gear'], key: 'roles'},
  {icon: ['fas', 'file-export'], key: 'export'},
  {icon: ['fas', 'shield'], key: 'gdpr'},
  {icon: ['fas', 'arrow-right-arrow-left'], key: 'transfer'},
  {icon: ['fas', 'puzzle-piece'], key: 'modules'},
]
</script>

<template>
  <div itemscope itemtype="https://schema.org/WebPage">
    <!-- Hero Section -->
    <section aria-label="Einleitung" class="relative overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-b from-primary/10 via-secondary/5 to-transparent"/>
      <div class="relative mx-auto max-w-5xl px-6 py-20 sm:py-28 text-center">
        <img src="/logo.png" alt="Ember" class="h-20 w-20 rounded-2xl mb-6 mx-auto" />
        <h1 class="text-4xl sm:text-5xl font-extrabold tracking-tight mb-4">
          {{ t('landing.heroTitle') }}
        </h1>
        <p class="text-lg sm:text-xl text-(--text-muted) max-w-2xl mx-auto mb-8 leading-relaxed">
          {{ t('landing.heroSubtitle') }}
        </p>
        <div class="flex items-center justify-center gap-4 flex-wrap">
          <router-link to="/apply">
            <PrimaryButton class="text-base px-6 py-3">
              <font-awesome-icon :icon="['fas', 'building']" class="mr-2"/>
              {{ t('landing.cta') }}
            </PrimaryButton>
          </router-link>
          <router-link to="/login">
            <SecondaryButton class="text-base px-6 py-3">
              {{ t('landing.login') }}
            </SecondaryButton>
          </router-link>
          <a v-if="demoUrl" :href="demoUrl" target="_blank" rel="noopener noreferrer">
            <InfoButton class="text-base px-6 py-3">
              <font-awesome-icon :icon="['fas', 'eye']" class="mr-2"/>
              {{ t('landing.demo') }}
            </InfoButton>
          </a>
          <router-link to="/helpcenter/station/basics">
            <SecondaryButton class="text-base px-6 py-3">
              <font-awesome-icon :icon="['fas', 'circle-question']" class="mr-2"/>
              {{ t('landing.helpCenter') }}
            </SecondaryButton>
          </router-link>
        </div>
      </div>
    </section>

    <!-- Features Grid -->
    <section aria-label="Funktionen" class="mx-auto max-w-5xl px-6 py-16">
      <h2 class="text-2xl sm:text-3xl font-bold text-center mb-3">
        {{ t('landing.featuresTitle') }}
      </h2>
      <p class="text-center text-(--text-muted) mb-12 max-w-xl mx-auto">
        {{ t('landing.featuresSubtitle') }}
      </p>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <router-link
            v-for="feature in features"
            :key="feature.key"
            :to="feature.help"
            class="rounded-xl border border-(--border) bg-(--bg) p-6 transition-all hover:border-primary/40 hover:shadow-lg"
        >
          <div class="flex items-center justify-center h-12 w-12 rounded-lg bg-primary/10 mb-4">
            <font-awesome-icon :icon="feature.icon" class="h-6 w-6 text-primary"/>
          </div>
          <h3 class="font-bold text-lg mb-2">{{ t(`landing.feature.${feature.key}.title`) }}</h3>
          <p class="text-sm text-(--text-muted) leading-relaxed">{{ t(`landing.feature.${feature.key}.desc`) }}</p>
        </router-link>
      </div>
    </section>

    <!-- Highlights / Why Ember -->
    <section aria-label="Vorteile" class="bg-(--bg-accent) py-16">
      <div class="mx-auto max-w-5xl px-6">
        <h2 class="text-2xl sm:text-3xl font-bold text-center mb-3">
          {{ t('landing.highlightsTitle') }}
        </h2>
        <p class="text-center text-(--text-muted) mb-12 max-w-xl mx-auto">
          {{ t('landing.highlightsSubtitle') }}
        </p>
        <div class="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          <div
              v-for="h in highlights"
              :key="h.key"
              class="flex items-start gap-4 rounded-lg p-4"
          >
            <font-awesome-icon :icon="h.icon" class="h-5 w-5 text-secondary mt-0.5 shrink-0"/>
            <div>
              <h4 class="font-semibold text-sm mb-1">{{ t(`landing.highlight.${h.key}.title`) }}</h4>
              <p class="text-xs text-(--text-muted) leading-relaxed">{{ t(`landing.highlight.${h.key}.desc`) }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Target Audience -->
    <section aria-label="Zielgruppe" class="mx-auto max-w-5xl px-6 py-16">
      <div class="rounded-2xl border border-(--border) bg-(--bg) p-8 sm:p-12 text-center">
        <img src="/logo.png" alt="Ember" class="h-12 w-12 rounded-xl mx-auto mb-4" />
        <h2 class="text-2xl font-bold mb-3">{{ t('landing.audienceTitle') }}</h2>
        <p class="text-(--text-muted) max-w-2xl mx-auto mb-6 leading-relaxed">
          {{ t('landing.audienceText') }}
        </p>
        <div class="flex flex-wrap justify-center gap-3">
          <span class="rounded-full bg-primary/10 text-primary px-4 py-1.5 text-sm font-medium">{{ t('landing.audienceTag1') }}</span>
          <span class="rounded-full bg-secondary/10 text-secondary px-4 py-1.5 text-sm font-medium">{{ t('landing.audienceTag2') }}</span>
          <span class="rounded-full bg-success/10 text-success px-4 py-1.5 text-sm font-medium">{{ t('landing.audienceTag3') }}</span>
          <span class="rounded-full bg-info/10 text-info px-4 py-1.5 text-sm font-medium">{{ t('landing.audienceTag4') }}</span>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section aria-label="Jetzt starten" class="bg-primary/5 py-16">
      <div class="mx-auto max-w-3xl px-6 text-center">
        <h2 class="text-2xl sm:text-3xl font-bold mb-3">{{ t('landing.ctaTitle') }}</h2>
        <p class="text-(--text-muted) mb-8 max-w-xl mx-auto">
          {{ t('landing.ctaText') }}
        </p>
        <div class="flex items-center justify-center gap-4 flex-wrap">
          <router-link to="/apply">
            <PrimaryButton class="text-base px-8 py-3">
              <font-awesome-icon :icon="['fas', 'building']" class="mr-2"/>
              {{ t('landing.cta') }}
            </PrimaryButton>
          </router-link>
          <a v-if="demoUrl" :href="demoUrl" target="_blank" rel="noopener noreferrer">
            <SecondaryButton class="text-base px-6 py-3">
              <font-awesome-icon :icon="['fas', 'eye']" class="mr-2"/>
              {{ t('landing.demo') }}
            </SecondaryButton>
          </a>
          <router-link to="/helpcenter/station/basics">
            <SecondaryButton class="text-base px-6 py-3">
              <font-awesome-icon :icon="['fas', 'circle-question']" class="mr-2"/>
              {{ t('landing.helpCenter') }}
            </SecondaryButton>
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>
