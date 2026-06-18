/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

const {t, tm, rt} = useI18n()

const ICONS: Array<[string, string]> = [
  ['fas', 'clipboard-user'],
  ['fas', 'calendar-days'],
  ['fas', 'boxes-stacked'],
  ['fas', 'users'],
  ['fas', 'square-poll-vertical'],
  ['fas', 'newspaper'],
  ['fas', 'book-open'],
  ['fas', 'graduation-cap'],
  ['fas', 'clipboard-check'],
  ['fas', 'globe'],
  ['fas', 'arrow-right-arrow-left'],
  ['fas', 'bell'],
]

const features = computed(() =>
    (tm('landingAlt.features.items') as Array<{title: string; body: string}>).map((f, i) => ({
      icon: ICONS[i] ?? ICONS[0],
      title: rt(f.title),
      body: rt(f.body),
    })),
)
</script>

<template>
  <section class="features">
    <header class="head">
      <div class="eyebrow">{{ t('landingAlt.features.eyebrow') }}</div>
      <SectionHeader class="display">{{ t('landingAlt.features.headline') }}</SectionHeader>
    </header>
    <ul class="grid">
      <li v-for="(f, i) in features" :key="i" class="card">
        <font-awesome-icon :icon="f.icon" class="icon"/>
        <SubHeader class="title">{{ f.title }}</SubHeader>
        <p class="body">{{ f.body }}</p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.features {
  padding: 5rem 1.5rem;
  max-width: 76rem;
  margin: 0 auto;
}
.head {
  margin-bottom: 3rem;
  max-width: 36rem;
}
.eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.55;
  margin-bottom: 0.75rem;
}
.display :deep(h2) {
  font-family: 'Bitter', Georgia, serif;
  font-size: clamp(1.65rem, 3.2vw, 2.25rem);
  line-height: 1.15;
  letter-spacing: -0.005em;
}
.grid {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1px;
  background: var(--border);
  border: 1px solid var(--border);
  border-radius: var(--radius-theme);
  overflow: hidden;
}
@media (min-width: 640px) {
  .grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (min-width: 960px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
.card {
  background: var(--bg);
  padding: 1.75rem 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.icon {
  height: 1.15rem;
  width: 1.15rem;
  color: var(--color-primary);
  margin-bottom: 0.4rem;
}
.title :deep(h3) {
  font-family: 'Bitter', Georgia, serif;
  font-size: 1.15rem;
  font-weight: 700;
  margin: 0;
}
.body {
  font-size: 0.95rem;
  opacity: 0.75;
  margin: 0;
  line-height: 1.5;
}
</style>
