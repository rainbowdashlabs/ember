/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'

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
  <section class="federation">
    <div class="grid">
      <div class="copy">
        <div class="eyebrow">{{ t('landing.federation.eyebrow') }}</div>
        <SectionHeader class="display">{{ t('landing.federation.headline') }}</SectionHeader>
        <p class="lede">{{ t('landing.federation.lede') }}</p>
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
.federation {
  padding: 6rem 1.5rem;
  max-width: 76rem;
  margin: 0 auto;
}
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 3rem;
  align-items: start;
}
@media (min-width: 900px) {
  .grid {
    grid-template-columns: 1fr 1fr;
    gap: 4rem;
  }
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
  font-size: clamp(1.85rem, 3.6vw, 2.5rem);
  line-height: 1.1;
  letter-spacing: -0.005em;
  margin: 0 0 1.5rem;
}
.lede {
  font-size: 1.05rem;
  line-height: 1.6;
  opacity: 0.78;
  margin: 0;
  max-width: 32rem;
}
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
