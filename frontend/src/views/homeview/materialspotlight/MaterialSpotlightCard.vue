/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MaterialExchangesSection from './MaterialExchangesSection.vue'
import MaterialProcurementsSection from './MaterialProcurementsSection.vue'
import MaterialLossesSection from './MaterialLossesSection.vue'
import type {ExchangeRow, ProcurementRow, LossRow} from './materialBadges'

const props = defineProps<{
  exchanges: ExchangeRow[]
  procurements: ProcurementRow[]
  losses: LossRow[]
}>()

const {t} = useI18n()

const totalCount = computed(() => props.exchanges.length + props.procurements.length + props.losses.length)
</script>

<template>
  <aside class="card" :aria-label="t('landing.material.card.aria')">
    <div class="card-head">
      <span class="card-title">{{ t('landing.material.card.title') }}</span>
      <span class="card-count">{{ t('landing.material.card.count', {n: totalCount}) }}</span>
    </div>
    <MaterialExchangesSection :exchanges="exchanges"/>
    <MaterialProcurementsSection :procurements="procurements"/>
    <MaterialLossesSection :losses="losses"/>
  </aside>
</template>

<style scoped>
.card {
  border: 1px solid var(--border);
  border-radius: var(--radius-theme);
  background: var(--bg);
  overflow: hidden;
  box-shadow: 0 12px 32px -18px rgba(0, 0, 0, 0.18);
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: 0.9rem 1.25rem;
  border-bottom: 1px solid var(--border);
  background: color-mix(in srgb, var(--border) 35%, var(--bg));
}
.card-title {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.85rem;
  font-weight: 700;
}
.card-count {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.75rem;
  opacity: 0.6;
}
</style>
