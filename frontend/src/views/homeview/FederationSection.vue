/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import LandingEyebrow from '@/views/homeview/LandingEyebrow.vue'

const {t, tm, rt} = useI18n()

const ICONS: Array<[string, string]> = [
  ['fas', 'calendar-days'],
  ['fas', 'boxes-stacked'],
  ['fas', 'book-open'],
  ['fas', 'people-group'],
]

const benefits = computed(() =>
    (tm('landing.federation.benefits') as string[]).map((text, i) => ({
      icon: ICONS[i] ?? ICONS[0],
      text: rt(text),
    })),
)
</script>

<template>
  <section class="landing-section">
    <div class="landing-grid-2col">
      <div class="copy">
        <LandingEyebrow>{{ t('landing.federation.eyebrow') }}</LandingEyebrow>
        <SectionHeader class="landing-h2">{{ t('landing.federation.headline') }}</SectionHeader>
        <p class="landing-lede">{{ t('landing.federation.lede') }}</p>
      </div>
      <ul class="list">
        <li v-for="b in benefits" :key="b.text" class="row">
          <font-awesome-icon :icon="b.icon" class="icon"/>
          <span class="text">{{ b.text }}</span>
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.row {
  display: grid;
  grid-template-columns: 1.5rem 1fr;
  gap: 1rem;
  align-items: baseline;
  padding-bottom: 1.25rem;
  border-bottom: 1px dashed var(--border);
}
.row:last-child {
  border-bottom: none;
}
.icon {
  height: 1rem;
  width: 1rem;
  color: var(--color-primary);
  margin-top: 0.2rem;
}
.text {
  font-size: 1rem;
  line-height: 1.55;
}
</style>
