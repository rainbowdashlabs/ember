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

const POINT_ICONS: Array<[string, string]> = [
  ['fas', 'circle-question'],
  ['fas', 'mobile-screen'],
  ['fas', 'palette'],
  ['fas', 'moon'],
  ['fas', 'compass'],
]

const steps = computed(() => (tm('landingAlt.usability.help.steps') as string[]).map(rt))
const points = computed(() =>
    (tm('landingAlt.usability.points') as string[]).map((text, i) => ({
      icon: POINT_ICONS[i] ?? POINT_ICONS[0],
      text: rt(text),
    })),
)
</script>

<template>
  <section class="usability">
    <div class="grid">
      <aside class="help" :aria-label="t('landingAlt.usability.help.aria')">
        <div class="help-head">
          <div class="help-crumbs">
            <font-awesome-icon :icon="['fas', 'circle-question']" class="help-icon"/>
            <span class="help-crumb">{{ t('landingAlt.usability.help.crumbRoot') }}</span>
            <span class="help-sep">/</span>
            <span class="help-crumb">{{ t('landingAlt.usability.help.crumbSection') }}</span>
          </div>
          <span class="help-close" aria-hidden="true">×</span>
        </div>
        <div class="help-body">
          <div class="help-eye">{{ t('landingAlt.usability.help.eyebrow') }}</div>
          <div class="help-title">{{ t('landingAlt.usability.help.title') }}</div>
          <ol class="help-steps">
            <li v-for="(s, i) in steps" :key="i">
              <span class="help-step-no">{{ String(i + 1).padStart(2, '0') }}</span>
              <span class="help-step-text">{{ s }}</span>
            </li>
          </ol>
          <div class="help-foot">
            <font-awesome-icon :icon="['fas', 'arrow-right']" class="help-foot-icon"/>
            <span>{{ t('landingAlt.usability.help.foot') }}</span>
          </div>
        </div>
      </aside>

      <div class="copy">
        <div class="eyebrow">{{ t('landingAlt.usability.eyebrow') }}</div>
        <SectionHeader class="display">{{ t('landingAlt.usability.headline') }}</SectionHeader>
        <p class="lede">{{ t('landingAlt.usability.lede') }}</p>
        <ul class="points">
          <li v-for="p in points" :key="p.text" class="point">
            <font-awesome-icon :icon="p.icon" class="point-icon"/>
            <span class="point-text">{{ p.text }}</span>
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.usability {
  padding: 6rem 1.5rem;
  max-width: 76rem;
  margin: 0 auto;
  border-top: 1px solid var(--border);
}
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 3rem;
  align-items: start;
}
@media (min-width: 900px) {
  .grid {
    grid-template-columns: 1fr 1.05fr;
    gap: 4.5rem;
  }
}

.help {
  border: 1px solid var(--border);
  border-radius: var(--radius-theme);
  background: var(--bg);
  overflow: hidden;
  box-shadow: 0 18px 36px -20px rgba(0, 0, 0, 0.25);
}
.help-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.7rem 1.1rem;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--border) 35%, var(--bg));
}
.help-crumbs {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.72rem;
}
.help-icon {
  width: 0.8rem;
  height: 0.8rem;
  opacity: 0.6;
}
.help-crumb {
  opacity: 0.85;
}
.help-sep {
  opacity: 0.4;
}
.help-close {
  font-size: 1.05rem;
  line-height: 1;
  opacity: 0.5;
  font-family: 'JetBrains Mono', monospace;
}
.help-body {
  padding: 1.5rem 1.4rem 1.6rem;
}
.help-eye {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.5;
  margin-bottom: 0.5rem;
}
.help-title {
  font-family: 'Bitter', Georgia, serif;
  font-size: 1.4rem;
  line-height: 1.2;
  font-weight: 700;
  margin: 0 0 1.25rem;
}
.help-steps {
  list-style: none;
  padding: 0;
  margin: 0 0 1.25rem;
  display: flex;
  flex-direction: column;
}
.help-steps li {
  display: grid;
  grid-template-columns: 2.4rem 1fr;
  gap: 0.85rem;
  align-items: baseline;
  padding: 0.65rem 0;
  border-top: 1px solid var(--border);
}
.help-steps li:last-child {
  border-bottom: 1px solid var(--border);
}
.help-step-no {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.75rem;
  opacity: 0.5;
  letter-spacing: 0.05em;
}
.help-step-text {
  font-size: 0.95rem;
  line-height: 1.5;
}
.help-foot {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--color-primary);
  font-weight: 500;
}
.help-foot-icon {
  width: 0.7rem;
  height: 0.7rem;
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
  margin: 0 0 1.25rem;
}
.lede {
  font-size: 1.05rem;
  line-height: 1.6;
  opacity: 0.78;
  margin: 0 0 1.75rem;
  max-width: 32rem;
}
.points {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
}
.point {
  display: grid;
  grid-template-columns: 1.4rem 1fr;
  gap: 0.85rem;
  align-items: baseline;
  padding: 0.85rem 0;
  border-top: 1px dashed var(--border);
}
.point:last-child {
  border-bottom: 1px dashed var(--border);
}
.point-icon {
  color: var(--color-primary);
  width: 0.95rem;
  height: 0.95rem;
  margin-top: 0.15rem;
}
.point-text {
  font-size: 0.98rem;
  line-height: 1.5;
}
</style>
