/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MaterialSectionHeader from './MaterialSectionHeader.vue'
import MaterialItemCell from './MaterialItemCell.vue'
import {typeBadge, useMaterialLabels, type ProcurementRow} from './materialBadges'

defineProps<{
  procurements: ProcurementRow[]
}>()

const {t} = useI18n()
const {typeLabel} = useMaterialLabels()
</script>

<template>
  <MaterialSectionHeader
      :icon="['fas', 'folder-plus']"
      :title="t('landing.material.sections.procurements')"
      :count="procurements.length"
  />
  <table class="landing-material-table">
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
          <MaterialItemCell :name="p.item" :size="p.size"/>
        </td>
        <td><component :is="typeBadge(p.type)">{{ typeLabel(p.type) }}</component></td>
        <td class="muted">{{ p.notes }}</td>
        <td class="r muted">{{ p.requested }}</td>
      </tr>
    </tbody>
  </table>
</template>

