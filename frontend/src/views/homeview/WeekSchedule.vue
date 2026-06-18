/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

const {t, tm, rt} = useI18n()

const entries = computed(() =>
    (tm('landing.week.entries') as Array<{day: string; time: string; text: string}>).map(e => ({
      day: rt(e.day),
      time: rt(e.time),
      text: rt(e.text),
    })),
)
</script>

<template>
  <aside class="schedule" :aria-label="t('landing.week.eyebrow')">
    <div class="schedule-eyebrow">{{ t('landing.week.eyebrow') }}</div>
    <ul class="schedule-list">
      <li v-for="entry in entries" :key="entry.day + entry.text" class="schedule-row">
        <span class="schedule-day">{{ entry.day }}</span>
        <span class="schedule-time">{{ entry.time }}</span>
        <span class="schedule-text">{{ entry.text }}</span>
      </li>
    </ul>
    <div class="schedule-foot">{{ t('landing.week.footer') }}</div>
  </aside>
</template>

<style scoped>
.schedule {
  border: 1px solid var(--border);
  background: var(--bg);
  padding: 1.5rem 1.25rem 1.25rem;
  border-radius: var(--radius-theme);
}
.schedule-eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.75rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.6;
  margin-bottom: 1.25rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}
.schedule-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
.schedule-row {
  display: grid;
  grid-template-columns: 2.25rem 3.5rem 1fr;
  gap: 1rem;
  align-items: baseline;
  font-size: 0.95rem;
}
.schedule-day {
  font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  font-weight: 500;
  opacity: 0.85;
}
.schedule-time {
  font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
  opacity: 0.7;
}
.schedule-text {
  opacity: 0.95;
}
.schedule-foot {
  margin-top: 1.25rem;
  padding-top: 0.75rem;
  border-top: 1px dashed var(--border);
  font-size: 0.75rem;
  opacity: 0.55;
}
</style>
