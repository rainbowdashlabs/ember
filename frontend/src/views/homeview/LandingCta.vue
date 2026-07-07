/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

defineProps<{
  registrationEnabled: boolean
  demoUrl: string
  isDemo: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <router-link v-if="registrationEnabled" to="/apply">
    <PrimaryButton :icon="['fas', 'building']" class="cta">
      {{ t('landing.hero.ctaCreate') }}
    </PrimaryButton>
  </router-link>
  <a v-else-if="demoUrl && !isDemo" :href="demoUrl" target="_blank" rel="noopener noreferrer">
    <PrimaryButton :icon="['fas', 'play']" class="cta">
      {{ t('landing.hero.ctaDemo') }}
    </PrimaryButton>
  </a>
  <router-link v-else to="/helpcenter/station/basics/hosting">
    <PrimaryButton :icon="['fas', 'server']" class="cta">
      {{ t('landing.hero.ctaHost') }}
    </PrimaryButton>
  </router-link>
</template>

<style scoped>
.cta {
  padding: 0.85rem 1.5rem;
  font-size: 1rem;
}
</style>
