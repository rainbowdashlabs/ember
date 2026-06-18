/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'

type InvType = 'INTERNAL' | 'EXTERNAL' | 'MIXED'
type ExchangeStatus = 'ANNOUNCED' | 'SHIPPED'

const {t, tm, rt} = useI18n()

interface ExchangeRow {item: string; size: string; type: InvType; owner: string; status: ExchangeStatus}
interface ProcurementRow {item: string; size: string; type: InvType; notes: string; requested: string}
interface LossRow {item: string; size: string; type: InvType; owner: string; lostAt: string}

const exchanges = computed(() =>
    (tm('landing.material.mock.exchanges') as ExchangeRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, owner: rt(r.owner), status: r.status,
    })),
)
const procurements = computed(() =>
    (tm('landing.material.mock.procurements') as ProcurementRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, notes: rt(r.notes), requested: rt(r.requested),
    })),
)
const losses = computed(() =>
    (tm('landing.material.mock.losses') as LossRow[]).map(r => ({
      item: rt(r.item), size: rt(r.size), type: r.type, owner: rt(r.owner), lostAt: rt(r.lostAt),
    })),
)
const totalCount = computed(() => exchanges.value.length + procurements.value.length + losses.value.length)
const bullets = computed(() => (tm('landing.material.bullets') as string[]).map(rt))

function typeBadge(type: InvType) {
  if (type === 'INTERNAL') return InfoBadge
  if (type === 'EXTERNAL') return SecondaryBadge
  return SuccessBadge
}
function typeLabel(type: InvType) {
  return t(`landing.material.type.${type}`)
}
function exchangeStatusBadge(s: ExchangeStatus) {
  return s === 'SHIPPED' ? InfoBadge : SecondaryBadge
}
function exchangeStatusLabel(s: ExchangeStatus) {
  return t(`landing.material.exchangeStatus.${s}`)
}
</script>

<template>
  <section class="spotlight">
    <div class="grid">
      <aside class="card" :aria-label="t('landing.material.card.aria')">
        <div class="card-head">
          <span class="card-title">{{ t('landing.material.card.title') }}</span>
          <span class="card-count">{{ t('landing.material.card.count', {n: totalCount}) }}</span>
        </div>

        <div class="sub">
          <font-awesome-icon :icon="['fas', 'rotate']" class="sub-icon"/>
          <span class="sub-title">{{ t('landing.material.sections.exchanges') }}</span>
          <span class="sub-count">({{ exchanges.length }})</span>
        </div>
        <table class="t">
          <colgroup>
            <col class="c-item"><col class="c-type"><col class="c-mid"><col class="c-end">
          </colgroup>
          <thead>
            <tr>
              <th>{{ t('landing.material.columns.entry') }}</th>
              <th>{{ t('landing.material.columns.type') }}</th>
              <th>{{ t('landing.material.columns.owner') }}</th>
              <th class="r">{{ t('landing.material.columns.status') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(e, i) in exchanges" :key="`x-${i}`">
              <td>
                <div class="cell-item"><span class="name">{{ e.item }}</span><SizeBadge>{{ e.size }}</SizeBadge></div>
              </td>
              <td><component :is="typeBadge(e.type)">{{ typeLabel(e.type) }}</component></td>
              <td class="muted">{{ e.owner }}</td>
              <td class="r"><component :is="exchangeStatusBadge(e.status)">{{ exchangeStatusLabel(e.status) }}</component></td>
            </tr>
          </tbody>
        </table>

        <div class="sub">
          <font-awesome-icon :icon="['fas', 'folder-plus']" class="sub-icon"/>
          <span class="sub-title">{{ t('landing.material.sections.procurements') }}</span>
          <span class="sub-count">({{ procurements.length }})</span>
        </div>
        <table class="t">
          <colgroup>
            <col class="c-item"><col class="c-type"><col class="c-mid"><col class="c-end">
          </colgroup>
          <thead>
            <tr>
              <th>{{ t('landing.material.columns.entry') }}</th>
              <th>{{ t('landing.material.columns.type') }}</th>
              <th>{{ t('landing.material.columns.note') }}</th>
              <th class="r">{{ t('landing.material.columns.requested') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(p, i) in procurements" :key="`p-${i}`">
              <td>
                <div class="cell-item"><span class="name">{{ p.item }}</span><SizeBadge>{{ p.size }}</SizeBadge></div>
              </td>
              <td><component :is="typeBadge(p.type)">{{ typeLabel(p.type) }}</component></td>
              <td class="muted">{{ p.notes }}</td>
              <td class="r muted">{{ p.requested }}</td>
            </tr>
          </tbody>
        </table>

        <div class="sub">
          <font-awesome-icon :icon="['fas', 'triangle-exclamation']" class="sub-icon sub-icon--warn"/>
          <span class="sub-title">{{ t('landing.material.sections.losses') }}</span>
          <span class="sub-count">({{ losses.length }})</span>
        </div>
        <table class="t t--last">
          <colgroup>
            <col class="c-item"><col class="c-type"><col class="c-mid"><col class="c-end">
          </colgroup>
          <thead>
            <tr>
              <th>{{ t('landing.material.columns.entry') }}</th>
              <th>{{ t('landing.material.columns.type') }}</th>
              <th>{{ t('landing.material.columns.owner') }}</th>
              <th class="r">{{ t('landing.material.columns.lostSince') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(l, i) in losses" :key="`l-${i}`">
              <td>
                <div class="cell-item"><span class="name">{{ l.item }}</span><SizeBadge>{{ l.size }}</SizeBadge></div>
              </td>
              <td><component :is="typeBadge(l.type)">{{ typeLabel(l.type) }}</component></td>
              <td class="muted">{{ l.owner }}</td>
              <td class="r"><ErrorBadge>{{ l.lostAt }}</ErrorBadge></td>
            </tr>
          </tbody>
        </table>
      </aside>

      <div class="copy">
        <div class="eyebrow">{{ t('landing.material.eyebrow') }}</div>
        <SectionHeader class="display">{{ t('landing.material.headline') }}</SectionHeader>
        <p class="lede">{{ t('landing.material.lede') }}</p>
        <ul class="bullets">
          <li v-for="b in bullets" :key="b">
            <font-awesome-icon :icon="['fas', 'check']" class="b-icon"/>{{ b }}
          </li>
        </ul>
      </div>
    </div>
  </section>
</template>

<style scoped>
.spotlight {
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
    grid-template-columns: 1.1fr 1fr;
    gap: 4.5rem;
  }
}

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

.sub {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 1rem 1.25rem 0.5rem;
}
.sub-icon {
  width: 0.8rem;
  height: 0.8rem;
  opacity: 0.6;
}
.sub-icon--warn { color: var(--color-error); opacity: 0.85; }
.sub-title {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.85rem;
  font-weight: 700;
}
.sub-count {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.72rem;
  opacity: 0.5;
}

.t {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 0.82rem;
}
.c-item { width: 42%; }
.c-type { width: 16%; }
.c-mid  { width: 22%; }
.c-end  { width: 20%; }

.t th {
  text-align: left;
  font-weight: 500;
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  opacity: 0.5;
  padding: 0.4rem 0.75rem 0.4rem 0;
  border-bottom: 1px solid var(--border);
}
.t th:first-child { padding-left: 1.25rem; }
.t th:last-child { padding-right: 1.25rem; }
.t td {
  padding: 0.65rem 0.75rem 0.65rem 0;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.t td:first-child { padding-left: 1.25rem; }
.t td:last-child { padding-right: 1.25rem; }
.t--last tbody tr:last-child td { border-bottom: none; }
.t .r { text-align: right; }

.cell-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}
.cell-item .name {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.muted {
  opacity: 0.65;
  font-size: 0.78rem;
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
.bullets {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}
.bullets li {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  font-size: 0.95rem;
  opacity: 0.85;
}
.b-icon {
  color: var(--color-primary);
  width: 0.85rem;
  height: 0.85rem;
  flex-shrink: 0;
}
</style>
