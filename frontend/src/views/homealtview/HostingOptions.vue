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
  ['fas', 'server'],
  ['fas', 'handshake'],
  ['fas', 'hand-holding'],
]

const options = computed(() =>
    (tm('landingAlt.hosting.options') as Array<{title: string; body: string}>).map((o, i) => ({
      icon: ICONS[i] ?? ICONS[0],
      title: rt(o.title),
      body: rt(o.body),
    })),
)
</script>

<template>
  <section class="hosting">
    <header class="head">
      <div class="eyebrow">{{ t('landingAlt.hosting.eyebrow') }}</div>
      <SectionHeader class="display">{{ t('landingAlt.hosting.headline') }}</SectionHeader>
    </header>
    <ul class="grid">
      <li v-for="(o, i) in options" :key="i" class="card">
        <font-awesome-icon :icon="o.icon" class="icon"/>
        <SubHeader class="title">{{ o.title }}</SubHeader>
        <p class="body">{{ o.body }}</p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.hosting {
  background: color-mix(in srgb, var(--color-primary) 4%, var(--bg));
  padding: 5rem 1.5rem;
}
.head {
  margin: 0 auto 3rem;
  max-width: 76rem;
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
  margin: 0;
}
.grid {
  list-style: none;
  padding: 0;
  margin: 0 auto;
  max-width: 76rem;
  display: grid;
  grid-template-columns: 1fr;
  gap: 1.25rem;
}
@media (min-width: 800px) {
  .grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
.card {
  background: var(--bg);
  border: 1px solid var(--border);
  padding: 1.75rem 1.5rem;
  border-radius: var(--radius-theme);
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.icon {
  height: 1.5rem;
  width: 1.5rem;
  color: var(--color-primary);
  margin-bottom: 0.6rem;
}
.title :deep(h3) {
  font-family: 'Bitter', Georgia, serif;
  font-size: 1.2rem;
  font-weight: 700;
  margin: 0;
}
.body {
  font-size: 0.95rem;
  opacity: 0.75;
  margin: 0;
  line-height: 1.55;
}
</style>
