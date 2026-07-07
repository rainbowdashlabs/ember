/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import LandingCta from '@/views/homeview/LandingCta.vue'

defineProps<{
  registrationEnabled: boolean
  demoUrl: string
  isDemo: boolean
  center?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="actions" :class="{'actions--center': center}">
    <LandingCta :registration-enabled="registrationEnabled" :demo-url="demoUrl" :is-demo="isDemo"/>
    <a v-if="registrationEnabled && demoUrl && !isDemo" :href="demoUrl" target="_blank" rel="noopener noreferrer" class="link-quiet">
      {{ t('landing.hero.linkDemo') }}
    </a>
    <router-link v-else-if="isDemo" to="/login" class="link-quiet">
      {{ t('landing.hero.linkLoginDemo') }}
    </router-link>
    <router-link v-else to="/login" class="link-quiet">
      {{ t('landing.hero.linkLogin') }}
    </router-link>
  </div>
</template>

<style scoped>
.actions {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  flex-wrap: wrap;
}
.actions--center {
  justify-content: center;
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
