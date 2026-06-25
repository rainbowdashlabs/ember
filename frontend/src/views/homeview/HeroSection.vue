/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogo} from '@/composables/useEmberLogo'
import WeekSchedule from '@/views/homeview/WeekSchedule.vue'
import HeroActions from '@/views/homeview/HeroActions.vue'

defineProps<{
  registrationEnabled: boolean
  demoUrl: string
  isDemo: boolean
}>()

const {t} = useI18n()
const logo = emberLogo()
</script>

<template>
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
        <PageHeader class="landing-h1">
          {{ t('landing.hero.titleLine1') }}<br>
          {{ t('landing.hero.titleLine2') }}
        </PageHeader>
        <p class="landing-lede-hero">{{ t('landing.hero.lede') }}</p>
        <HeroActions
            :registration-enabled="registrationEnabled"
            :demo-url="demoUrl"
            :is-demo="isDemo"/>
      </div>
      <WeekSchedule/>
    </div>
  </section>
</template>

<style scoped>
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
</style>
