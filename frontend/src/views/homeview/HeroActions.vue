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
  center?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="actions" :class="{'actions--center': center}">
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
