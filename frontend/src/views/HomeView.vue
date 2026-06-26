/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import HeroSection from '@/views/homeview/HeroSection.vue'
import MittwochAbend from '@/views/homeview/MittwochAbend.vue'
import MaterialSpotlight from '@/views/homeview/MaterialSpotlight.vue'
import FeaturesGrid from '@/views/homeview/FeaturesGrid.vue'
import HostingOptions from '@/views/homeview/HostingOptions.vue'
import ReplacesSection from '@/views/homeview/ReplacesSection.vue'
import FederationSection from '@/views/homeview/FederationSection.vue'
import UsabilitySection from '@/views/homeview/UsabilitySection.vue'
import FactsRow from '@/views/homeview/FactsRow.vue'

import '@fontsource/bitter/500.css'
import '@fontsource/bitter/700.css'
import '@fontsource/jetbrains-mono/500.css'

const {t} = useI18n()

const {data: publicConfig} = await useAsyncData(
    'home-public-config',
    () => $fetch<{demoUrl?: string; demo?: boolean}>('/api/v1/public/config').catch(() => ({} as {demoUrl?: string; demo?: boolean})),
    {default: () => ({demoUrl: '', demo: false})},
)
const {data: regStatus} = await useAsyncData(
    'home-registration-status',
    () =>
        $fetch<{enabled: boolean}>('/api/v1/public/settings/station-registration').catch(() => ({
          enabled: true,
        })),
    {default: () => ({enabled: true})},
)

const demoUrl = computed(() => publicConfig.value?.demoUrl ?? '')
const isDemo = computed(() => publicConfig.value?.demo ?? false)
const registrationEnabled = computed(() => regStatus.value?.enabled ?? true)

useHead({
  title: t('landing.meta.title'),
})
</script>

<template>
  <div class="home-root">
    <HeroSection
        :registration-enabled="registrationEnabled"
        :demo-url="demoUrl"
        :is-demo="isDemo"/>

    <MittwochAbend/>
    <ReplacesSection/>
    <MaterialSpotlight/>
    <UsabilitySection/>
    <FeaturesGrid/>
    <FederationSection/>
    <HostingOptions/>
    <FactsRow/>

    <section class="closing">
      <SectionHeader class="closing-title">
        {{ t('landing.closing.title') }}
      </SectionHeader>
      <div class="closing-actions mt-6">
        <router-link v-if="registrationEnabled" to="/apply">
          <PrimaryButton :icon="['fas', 'building']" class="cta">
            {{ t('landing.hero.ctaCreate') }}
          </PrimaryButton>
        </router-link>
        <router-link v-else to="/helpcenter/station/basics/hosting">
          <PrimaryButton :icon="['fas', 'server']" class="cta">
            {{ t('landing.hero.ctaHost') }}
          </PrimaryButton>
        </router-link>
        <router-link to="/helpcenter/station/basics" class="link-quiet">
          {{ t('landing.closing.linkHelp') }}
        </router-link>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-root {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  color: var(--text);
}

.closing {
  padding: 6rem 1.5rem 7rem;
  max-width: 50rem;
  margin: 0 auto;
  text-align: center;
}
.closing-title :deep(h2) {
  font-family: 'Bitter', Georgia, serif;
  font-weight: 700;
  font-size: clamp(1.85rem, 4vw, 2.75rem);
  line-height: 1.1;
  letter-spacing: -0.01em;
  margin: 0 0 2.25rem;
}
.closing-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.cta {
  padding: 0.85rem 1.5rem;
  font-size: 1rem;
}
.link-quiet {
  font-size: 0.95rem;
  color: var(--text);
  opacity: 0.7;
  text-decoration: none;
  border-bottom: 1px solid currentColor;
  padding-bottom: 1px;
  transition: opacity 0.15s ease;
}
.link-quiet:hover {
  opacity: 1;
}
</style>
