/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MaterialSectionHeader from './MaterialSectionHeader.vue'
import MaterialItemCell from './MaterialItemCell.vue'
import {typeBadge, useMaterialLabels, type LossRow} from './materialBadges'

defineProps<{
  losses: LossRow[]
}>()

const {t} = useI18n()
const {typeLabel} = useMaterialLabels()
</script>

<template>
  <MaterialSectionHeader
      :icon="['fas', 'triangle-exclamation']"
      icon-warn
      :title="t('landing.material.sections.losses')"
      :count="losses.length"
  />
  <table class="landing-material-table landing-material-table--last">
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
          <MaterialItemCell :name="l.item" :size="l.size"/>
        </td>
        <td><component :is="typeBadge(l.type)">{{ typeLabel(l.type) }}</component></td>
        <td class="muted">{{ l.owner }}</td>
        <td class="r"><ErrorBadge>{{ l.lostAt }}</ErrorBadge></td>
      </tr>
    </tbody>
  </table>
</template>
