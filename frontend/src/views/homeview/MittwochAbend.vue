/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PageHeader from '@/components/typography/PageHeader.vue'

const {t, tm, rt} = useI18n()

const beats = computed(() =>
    (tm('landing.evening.beats') as Array<{time: string; text: string}>).map(b => ({
      time: rt(b.time),
      text: rt(b.text),
    })),
)
</script>

<template>
  <section class="evening">
    <div class="eyebrow">{{ t('landing.evening.eyebrow') }}</div>
    <div class="grid">
      <div class="poster">
        <PageHeader class="title">
          {{ t('landing.evening.titleLine1') }}<br>
          {{ t('landing.evening.titleLine2') }}<br>
          {{ t('landing.evening.titleLine3') }}
        </PageHeader>
        <p class="kicker">{{ t('landing.evening.kicker') }}</p>
      </div>
      <ol class="timeline">
        <li v-for="b in beats" :key="b.time" class="beat">
          <time class="time">{{ b.time }}</time>
          <span class="text">{{ b.text }}</span>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.evening {
  padding: 6rem 1.5rem;
  max-width: 76rem;
  margin: 0 auto;
  border-top: 1px solid var(--border);
}
.eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.55;
  margin-bottom: 2.5rem;
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
    gap: 5rem;
  }
}
.title :deep(h1) {
  font-family: 'Bitter', Georgia, serif;
  font-weight: 700;
  font-size: clamp(2.5rem, 6vw, 4.5rem);
  line-height: 0.95;
  letter-spacing: -0.02em;
  margin: 0 0 2rem;
}
.kicker {
  font-family: 'Bitter', Georgia, serif;
  font-size: 1.15rem;
  line-height: 1.45;
  opacity: 0.78;
  margin: 0;
  max-width: 26rem;
}
.timeline {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--border);
}
.beat {
  display: grid;
  grid-template-columns: 4.5rem 1fr;
  gap: 1.5rem;
  padding: 1.1rem 0;
  border-bottom: 1px solid var(--border);
  align-items: baseline;
}
.time {
  font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.95rem;
  font-weight: 500;
  opacity: 0.55;
  letter-spacing: 0.02em;
}
.text {
  font-size: 1.02rem;
  line-height: 1.5;
}
</style>
