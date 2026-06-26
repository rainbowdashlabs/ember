/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ColumnEntry, TableEntry} from '@/api/dataTracking'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const props = defineProps<{
  columns: ColumnEntry[]
  foreignKeys?: TableEntry['foreignKeys']
}>()

const emit = defineEmits<{
  verifyAll: []
}>()

const {t} = useI18n()

const allVerified = computed(() => props.columns.every(c => c.verified))

function foreignKeyFor(column: string) {
  return (props.foreignKeys ?? []).find(fk => fk.column === column)
}

function foreignKeyTooltip(column: string): string {
  const fk = foreignKeyFor(column)
  if (!fk) return ''
  return `FK → ${fk.refTable}.${fk.refColumn} (${fk.onDelete})`
}
</script>

<template>
  <section>
    <SectionHeader class="!text-base">{{ t('adminDataTracking.detail.columns') }}</SectionHeader>
    <div class="rounded-theme border border-(--border)">
      <div
          v-for="col in columns"
          :key="col.name"
          class="px-3 py-1.5 border-b border-(--border) last:border-b-0"
      >
        <div class="flex items-center justify-between gap-2">
          <div class="font-mono text-sm flex items-center gap-2 min-w-0 flex-1">
            <font-awesome-icon
                v-if="foreignKeyFor(col.name)"
                :icon="['fas', 'key']"
                class="text-(--text-muted)"
                :title="foreignKeyTooltip(col.name)"
            />
            <span class="font-semibold truncate">{{ col.name }}</span>
            <span class="text-(--text-muted)">{{ col.type }}{{ col.nullable ? '?' : '' }}</span>
          </div>
          <ToggleInput v-model="col.verified" :label="t('adminDataTracking.detail.verified')"/>
        </div>
        <p
            v-if="col.description"
            class="text-xs text-(--text-muted) italic pl-1 mt-0.5"
        >
          {{ col.description }}
        </p>
      </div>
    </div>
    <div class="mt-2 flex items-center justify-between">
      <span class="text-xs text-(--text-muted)">
        {{ columns.filter(c => c.verified).length }} / {{ columns.length }}
        {{ t('adminDataTracking.detail.verifiedShort') }}
      </span>
      <SuccessButton
          v-if="!allVerified"
          :icon="['fas', 'check-double']"
          @click="emit('verifyAll')"
      >
        {{ t('adminDataTracking.detail.verifyAll') }}
      </SuccessButton>
    </div>
  </section>
</template>
