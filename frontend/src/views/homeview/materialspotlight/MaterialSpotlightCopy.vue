/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'

defineProps<{
  bullets: string[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="copy">
    <div class="eyebrow">{{ t('landing.material.eyebrow') }}</div>
    <SectionHeader class="landing-h2 landing-h2-tight">{{ t('landing.material.headline') }}</SectionHeader>
    <p class="landing-lede landing-lede-spaced">{{ t('landing.material.lede') }}</p>
    <ul class="bullets">
      <li v-for="b in bullets" :key="b">
        <font-awesome-icon :icon="['fas', 'check']" class="b-icon"/>{{ b }}
      </li>
    </ul>
  </div>
</template>

<style scoped>
.eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.55;
  margin-bottom: 0.75rem;
}
.bullets {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}
.bullets li {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  font-size: 0.95rem;
  opacity: 0.85;
}
.b-icon {
  color: var(--color-primary);
  width: 0.85rem;
  height: 0.85rem;
  flex-shrink: 0;
}
</style>
