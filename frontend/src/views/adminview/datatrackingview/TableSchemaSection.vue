/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {TableEntry} from '@/api/dataTracking'
import SectionHeader from '@/components/typography/SectionHeader.vue'

defineProps<{
  entry: TableEntry
}>()

const {t} = useI18n()
</script>

<template>
  <section v-if="entry.foreignKeys?.length">
    <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.foreignKeys') }}</SectionHeader>
    <ul class="text-xs font-mono space-y-1">
      <li v-for="fk in entry.foreignKeys" :key="fk.column">
        <span class="font-semibold">{{ fk.column }}</span> →
        <span>{{ fk.refTable }}.{{ fk.refColumn }}</span>
        <span class="ml-1 text-(--text-muted)">({{ fk.onDelete }})</span>
      </li>
    </ul>
  </section>

  <section v-if="entry.lookups?.length">
    <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.lookups') }}</SectionHeader>
    <ul class="text-xs font-mono space-y-1">
      <li v-for="lk in entry.lookups" :key="lk.emitAs">
        <span class="font-semibold">{{ lk.emitAs }}</span>
        ← {{ lk.via }} → {{ lk.pick }}
      </li>
    </ul>
  </section>

  <section v-if="entry.customScope">
    <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.customScope') }}</SectionHeader>
    <pre class="text-xs font-mono bg-(--bg-accent) rounded-theme p-3 overflow-x-auto">{{ JSON.stringify(entry.customScope, null, 2) }}</pre>
  </section>
</template>
