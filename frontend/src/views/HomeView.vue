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
import InfiniteReel from '@/components/display/InfiniteReel.vue'
import client from '@/api/client'
import {adminSettings} from '@/api'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import { emberLogo } from '@/composables/useEmberLogo'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t} = useI18n()
const logo = emberLogo()
const demoUrl = ref('')
const isDemo = ref(false)
const registrationEnabled = ref(true)

onMounted(async () => {
  try {
    const res = await client.get<{ demoUrl?: string; demo?: boolean }>('/public/config')
    demoUrl.value = res.data.demoUrl ?? ''
    isDemo.value = res.data.demo ?? false
  } catch { /* ignore */ }
  adminSettings.isRegistrationEnabled().then(v => registrationEnabled.value = v).catch(() => {})
})

const features = [
  {icon: ['fas', 'clipboard-user'], key: 'attendance', help: '/helpcenter/station/attendance/new'},
  {icon: ['fas', 'calendar-days'], key: 'events', help: '/helpcenter/station/events'},
  {icon: ['fas', 'boxes-stacked'], key: 'inventory', help: '/helpcenter/station/inventory/overview'},
  {icon: ['fas', 'users'], key: 'members', help: '/helpcenter/station/members/list'},
  {icon: ['fas', 'square-poll-vertical'], key: 'forms', help: '/helpcenter/station/forms'},
  {icon: ['fas', 'newspaper'], key: 'news', help: '/helpcenter/station/news'},
  {icon: ['fas', 'book-open'], key: 'knowledgeBase', help: '/helpcenter/station/knowledge'},
  {icon: ['fas', 'graduation-cap'], key: 'quiz', help: '/helpcenter/station/quiz/catalogs'},
  {icon: ['fas', 'clipboard-check'], key: 'protocol', help: '/helpcenter/station/protocols'},
  {icon: ['fas', 'list-check'], key: 'waitingList', help: '/helpcenter/station/members/waiting-lists'},
  {icon: ['fas', 'box-open'], key: 'lostAndFound', help: '/helpcenter/station/inventory/overview'},
  {icon: ['fas', 'arrow-right-arrow-left'], key: 'federation', help: '/helpcenter/station/federate'},
  {icon: ['fas', 'globe'], key: 'publicStation', help: '/helpcenter/station/federate/settings'},
  {icon: ['fas', 'file-lines'], key: 'publicPages', help: '/helpcenter/station/pages'},
  {icon: ['fas', 'bell'], key: 'notifications', help: '/helpcenter/station/profile/settings'},
  {icon: ['fas', 'circle-question'], key: 'helpCenter', help: '/helpcenter/station/basics'},
]

const highlights = [
  {icon: ['fas', 'mobile-screen'], key: 'responsive'},
  {icon: ['fas', 'moon'], key: 'darkMode'},
  {icon: ['fas', 'palette'], key: 'themes'},
  {icon: ['fas', 'lock'], key: 'secure'},
  {icon: ['fas', 'users-gear'], key: 'roles'},
  {icon: ['fas', 'file-export'], key: 'export'},
  {icon: ['fas', 'shield'], key: 'gdpr'},
  {icon: ['fas', 'puzzle-piece'], key: 'modules'},
  {icon: ['fas', 'server'], key: 'selfHosted'},
  {icon: ['fas', 'arrow-right-arrow-left'], key: 'transfer'},
]
</script>

<template>
  <div itemscope itemtype="https://schema.org/WebPage">
    <!-- Hero Section -->
    <section aria-label="Einleitung" class="relative overflow-hidden">
      <div class="absolute inset-0 bg-gradient-to-b from-primary/10 via-secondary/5 to-transparent"/>
      <div class="relative mx-auto max-w-5xl px-6 py-20 sm:py-28 text-center">
        <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" :auto-blink="true" :bounce="true" :gazePositions="['left', 'right', 'mid']" size="h-20 w-20 mb-6 mx-auto" :pixel-size="256" />
        <PageHeader class="text-4xl sm:text-5xl font-extrabold tracking-tight mb-4">
          {{ t('landing.heroTitle') }}
        </PageHeader>
        <p class="text-lg sm:text-xl text-(--text-muted) max-w-2xl mx-auto mb-8 leading-relaxed">
          {{ t('landing.heroSubtitle') }}
        </p>
        <div class="flex items-center justify-center gap-4 flex-wrap">
          <router-link v-if="registrationEnabled" to="/apply">
            <PrimaryButton :icon="['fas', 'building']" class="text-base px-6 py-3">
              {{ t('landing.cta') }}
            </PrimaryButton>
          </router-link>
          <router-link v-if="!isDemo" to="/login">
            <SecondaryButton class="text-base px-6 py-3">
              {{ t('landing.login') }}
            </SecondaryButton>
          </router-link>
          <router-link v-if="isDemo" to="/login">
            <PrimaryButton :icon="['fas', 'rocket']" class="text-base px-6 py-3">
              {{ t('landing.tryNow') }}
            </PrimaryButton>
          </router-link>
          <a v-if="demoUrl && !isDemo" :href="demoUrl" target="_blank" rel="noopener noreferrer">
            <InfoButton :icon="['fas', 'eye']" class="text-base px-6 py-3">
              {{ t('landing.demo') }}
            </InfoButton>
          </a>
          <router-link to="/helpcenter/station/basics">
            <SecondaryButton :icon="['fas', 'circle-question']" class="text-base px-6 py-3">
              {{ t('landing.helpCenter') }}
            </SecondaryButton>
          </router-link>
        </div>
      </div>
    </section>

    <!-- Features Reel -->
    <section aria-label="Funktionen" class="py-16">
      <div class="mx-auto max-w-5xl px-6">
        <SectionHeader class="text-2xl sm:text-3xl font-bold text-center mb-3">
          {{ t('landing.featuresTitle') }}
        </SectionHeader>
        <p class="text-center text-(--text-muted) mb-12 max-w-xl mx-auto">
          {{ t('landing.featuresSubtitle') }}
        </p>
      </div>
      <div class="mx-auto max-w-5xl px-6">
        <InfiniteReel :item-count="features.length">
          <router-link
            v-for="(feature, i) in [...features, ...features]"
            :key="`f-${i}`"
            :to="feature.help"
            class="shrink-0 rounded-xl border border-(--border) bg-(--bg) p-6 transition-shadow hover:border-primary/40 hover:shadow-lg"
            :style="{ width: 'var(--tile-w, 100%)' }"
          >
            <div class="flex items-center justify-center h-12 w-12 rounded-lg bg-primary/10 mb-4 mx-auto">
              <font-awesome-icon :icon="feature.icon" class="h-6 w-6 text-primary"/>
            </div>
            <SubHeader class="mb-2 text-center">{{ t(`landing.feature.${feature.key}.title`) }}</SubHeader>
            <p class="text-sm text-(--text-muted) leading-relaxed text-center">{{ t(`landing.feature.${feature.key}.desc`) }}</p>
          </router-link>
        </InfiniteReel>
      </div>
    </section>

    <!-- Highlights Reel -->
    <section aria-label="Vorteile" class="bg-(--bg-accent) py-16">
      <div class="mx-auto max-w-5xl px-6">
        <SectionHeader class="text-2xl sm:text-3xl font-bold text-center mb-3">
          {{ t('landing.highlightsTitle') }}
        </SectionHeader>
        <p class="text-center text-(--text-muted) mb-12 max-w-xl mx-auto">
          {{ t('landing.highlightsSubtitle') }}
        </p>
      </div>
      <div class="mx-auto max-w-5xl px-6">
        <InfiniteReel :item-count="highlights.length">
          <div
            v-for="(h, i) in [...highlights, ...highlights]"
            :key="`h-${i}`"
            class="shrink-0 rounded-xl border border-(--border) bg-(--bg) p-6"
            :style="{ width: 'var(--tile-w, 100%)' }"
          >
            <div class="flex items-center justify-center h-10 w-10 rounded-lg bg-secondary/10 mb-3 mx-auto">
              <font-awesome-icon :icon="h.icon" class="h-5 w-5 text-secondary"/>
            </div>
            <SubHeader class="text-sm mb-1 text-center">{{ t(`landing.highlight.${h.key}.title`) }}</SubHeader>
            <p class="text-xs text-(--text-muted) leading-relaxed text-center">{{ t(`landing.highlight.${h.key}.desc`) }}</p>
          </div>
        </InfiniteReel>
      </div>
    </section>

    <!-- Target Audience -->
    <section aria-label="Zielgruppe" class="mx-auto max-w-5xl px-6 py-16">
      <div class="rounded-2xl border border-(--border) bg-(--bg) p-8 sm:p-12 text-center">
        <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" size="h-12 w-12 mx-auto mb-4" :pixel-size="128" />
        <SectionHeader class="text-2xl font-bold mb-3">{{ t('landing.audienceTitle') }}</SectionHeader>
        <p class="text-(--text-muted) max-w-2xl mx-auto mb-6 leading-relaxed">
          {{ t('landing.audienceText') }}
        </p>
        <div class="flex flex-wrap justify-center gap-3">
          <PrimaryBadge>{{ t('landing.audienceTag1') }}</PrimaryBadge>
          <SecondaryBadge>{{ t('landing.audienceTag2') }}</SecondaryBadge>
          <SuccessBadge>{{ t('landing.audienceTag3') }}</SuccessBadge>
          <InfoBadge>{{ t('landing.audienceTag4') }}</InfoBadge>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section aria-label="Jetzt starten" class="bg-primary/5 py-16">
      <div class="mx-auto max-w-3xl px-6 text-center">
        <SectionHeader class="text-2xl sm:text-3xl font-bold mb-3">{{ registrationEnabled ? t('landing.ctaTitle') : t('landing.ctaTitleSelfHost') }}</SectionHeader>
        <p class="text-(--text-muted) mb-8 max-w-xl mx-auto">
          {{ registrationEnabled ? t('landing.ctaText') : t('landing.ctaTextSelfHost') }}
        </p>
        <div class="flex items-center justify-center gap-4 flex-wrap">
          <router-link v-if="registrationEnabled" to="/apply">
            <PrimaryButton :icon="['fas', 'building']" class="text-base px-8 py-3">
              {{ t('landing.cta') }}
            </PrimaryButton>
          </router-link>
          <router-link v-if="!registrationEnabled" to="/helpcenter/station/basics/hosting">
            <PrimaryButton :icon="['fas', 'server']" class="text-base px-8 py-3">
              {{ t('landing.selfHostCta') }}
            </PrimaryButton>
          </router-link>
          <router-link v-if="isDemo" to="/login">
            <PrimaryButton :icon="['fas', 'rocket']" class="text-base px-6 py-3">
              {{ t('landing.tryNow') }}
            </PrimaryButton>
          </router-link>
          <a v-if="demoUrl && !isDemo" :href="demoUrl" target="_blank" rel="noopener noreferrer">
            <SecondaryButton :icon="['fas', 'eye']" class="text-base px-6 py-3">
              {{ t('landing.demo') }}
            </SecondaryButton>
          </a>
          <router-link to="/helpcenter/station/basics">
            <SecondaryButton :icon="['fas', 'circle-question']" class="text-base px-6 py-3">
              {{ t('landing.helpCenter') }}
            </SecondaryButton>
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>
