/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const { t } = useI18n()
</script>

<template>
  <thead>
    <tr>
      <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-left min-w-32">{{ t('protocol.topic') }}</th>
      <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.maxPts') }}</th>
      <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-12">{{ t('protocol.avg') }}</th>
      <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20">
        <div class="font-medium mb-1">Lena Mueller</div>
        <PrimaryButton class="!text-xs !py-0.5 !px-2" disabled>
          <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
        </PrimaryButton>
      </th>
      <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20">
        <div class="font-medium mb-1">Max Maier</div>
        <PrimaryButton class="!text-xs !py-0.5 !px-2" disabled>
          <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
        </PrimaryButton>
      </th>
    </tr>
  </thead>
</template>
