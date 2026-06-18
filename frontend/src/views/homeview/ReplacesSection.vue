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

const items = computed(() =>
    (tm('landing.replaces.items') as Array<{old: string; neu: string}>).map(r => ({
      old: rt(r.old),
      neu: rt(r.neu),
    })),
)
</script>

<template>
  <section class="replaces">
    <header class="head">
      <div class="eyebrow">{{ t('landing.replaces.eyebrow') }}</div>
      <SectionHeader class="display">{{ t('landing.replaces.headline') }}</SectionHeader>
    </header>
    <ul class="grid">
      <li v-for="r in items" :key="r.old" class="row">
        <div class="old">{{ r.old }}</div>
        <font-awesome-icon :icon="['fas', 'arrow-right']" class="arrow"/>
        <div class="neu">{{ r.neu }}</div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.replaces {
  background: #111;
  color: #f5f3ef;
  padding: 5rem 1.5rem;
}
.head {
  max-width: 76rem;
  margin: 0 auto 3rem;
}
.eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.5;
  margin-bottom: 0.75rem;
  color: #f5f3ef;
}
.display :deep(h2) {
  font-family: 'Bitter', Georgia, serif;
  font-size: clamp(1.65rem, 3.2vw, 2.25rem);
  line-height: 1.15;
  letter-spacing: -0.005em;
  color: #f5f3ef;
}
.grid {
  list-style: none;
  padding: 0;
  margin: 0 auto;
  max-width: 76rem;
  display: flex;
  flex-direction: column;
}
.row {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 1.5rem;
  align-items: center;
  padding: 1.5rem 0;
  border-bottom: 1px solid #2a2a2a;
}
.row:last-child {
  border-bottom: none;
}
.old {
  text-align: right;
  font-size: 1.05rem;
  opacity: 0.75;
  text-decoration: line-through;
  text-decoration-thickness: 2px;
  text-decoration-color: #ff6421;
  text-decoration-skip-ink: none;
}
.arrow {
  height: 0.9rem;
  width: 0.9rem;
  opacity: 0.5;
}
.neu {
  font-family: 'Bitter', Georgia, serif;
  font-weight: 700;
  font-size: 1.15rem;
}
@media (max-width: 720px) {
  .row {
    grid-template-columns: 1fr;
    gap: 0.5rem;
    text-align: left;
  }
  .old {
    text-align: left;
  }
  .arrow {
    display: none;
  }
}
</style>
