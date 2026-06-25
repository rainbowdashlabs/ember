/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MaterialSectionHeader from './MaterialSectionHeader.vue'
import MaterialItemCell from './MaterialItemCell.vue'
import {typeBadge, exchangeStatusBadge, useMaterialLabels, type ExchangeRow} from './materialBadges'

defineProps<{
  exchanges: ExchangeRow[]
}>()

const {t} = useI18n()
const {typeLabel, exchangeStatusLabel} = useMaterialLabels()
</script>

<template>
  <MaterialSectionHeader
      :icon="['fas', 'rotate']"
      :title="t('landing.material.sections.exchanges')"
      :count="exchanges.length"
  />
  <table class="landing-material-table">
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
          <MaterialItemCell :name="e.item" :size="e.size"/>
        </td>
        <td><component :is="typeBadge(e.type)">{{ typeLabel(e.type) }}</component></td>
        <td class="muted">{{ e.owner }}</td>
        <td class="r"><component :is="exchangeStatusBadge(e.status)">{{ exchangeStatusLabel(e.status) }}</component></td>
      </tr>
    </tbody>
  </table>
</template>

