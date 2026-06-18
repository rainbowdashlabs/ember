/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

const {tm, rt} = useI18n()

const facts = computed(() =>
    (tm('landing.facts.items') as Array<{label: string; value: string; note: string}>).map(f => ({
      label: rt(f.label),
      value: rt(f.value),
      note: rt(f.note),
    })),
)
</script>

<template>
  <section class="facts">
    <dl class="grid">
      <div v-for="f in facts" :key="f.label" class="fact">
        <dt class="label">{{ f.label }}</dt>
        <dd class="value">{{ f.value }}</dd>
        <dd class="note">{{ f.note }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.facts {
  padding: 4rem 1.5rem;
  max-width: 76rem;
  margin: 0 auto;
}
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1px;
  background: var(--border);
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  margin: 0;
}
@media (min-width: 720px) {
  .grid {
    grid-template-columns: repeat(4, 1fr);
    border-left: 1px solid var(--border);
    border-right: 1px solid var(--border);
  }
}
.fact {
  background: var(--bg);
  padding: 1.75rem 1.5rem;
}
.label {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  font-weight: 700;
  opacity: 0.55;
  margin-bottom: 0.5rem;
}
.value {
  font-family: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 1rem;
  margin: 0 0 0.45rem;
  opacity: 0.95;
}
.note {
  font-size: 0.78rem;
  line-height: 1.45;
  opacity: 0.6;
  margin: 0;
}
</style>
