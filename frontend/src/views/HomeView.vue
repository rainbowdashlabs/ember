/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogo} from '@/composables/useEmberLogo'
import WeekSchedule from '@/views/homeview/WeekSchedule.vue'
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
const logo = emberLogo()

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
    <section class="hero">
      <div class="hero-grid">
        <div class="hero-copy">
          <LayeredEmberLogo
              :layers="logo.layers"
              :active-layers="logo.activeLayers"
              :auto-blink="true"
              :bounce="true"
              :gazePositions="['left', 'right', 'mid']"
              size="h-28 w-28 sm:h-36 sm:w-36 mb-10"
              :pixel-size="512"/>
          <PageHeader class="display">
            {{ t('landing.hero.titleLine1') }}<br>
            {{ t('landing.hero.titleLine2') }}
          </PageHeader>
          <p class="lede">{{ t('landing.hero.lede') }}</p>
          <div class="actions">
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
            <a v-if="demoUrl && !isDemo" :href="demoUrl" target="_blank" rel="noopener noreferrer" class="link-quiet">
              {{ t('landing.hero.linkDemo') }}
            </a>
            <router-link v-else-if="isDemo" to="/login" class="link-quiet">
              {{ t('landing.hero.linkLoginDemo') }}
            </router-link>
            <router-link v-else to="/login" class="link-quiet">
              {{ t('landing.hero.linkLogin') }}
            </router-link>
          </div>
        </div>
        <WeekSchedule/>
      </div>
    </section>

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
      <div class="actions actions--center mt-6">
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

.hero {
  padding: 6rem 1.5rem 5rem;
  max-width: 76rem;
  margin: 0 auto;
}
.hero-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 3.5rem;
  align-items: start;
}
@media (min-width: 900px) {
  .hero-grid {
    grid-template-columns: 1.2fr 1fr;
    gap: 4rem;
  }
}

.display :deep(h1) {
  font-family: 'Bitter', Georgia, 'Times New Roman', serif;
  font-weight: 700;
  font-size: clamp(2.25rem, 5vw, 3.5rem);
  line-height: 1.05;
  letter-spacing: -0.01em;
  margin: 0 0 1.25rem;
}
.lede {
  font-size: 1.125rem;
  line-height: 1.55;
  max-width: 34rem;
  opacity: 0.78;
  margin: 0 0 2rem;
}
.actions {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.actions--center {
  justify-content: center;
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
</style>
