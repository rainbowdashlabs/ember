/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <tbody>
    <tr class="font-semibold">
      <td class="border border-[var(--border)] px-2 py-1">Notruf</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">8</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">7.5</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">8</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">7</td>
    </tr>
    <tr class="font-semibold">
      <td class="border border-[var(--border)] px-2 py-1">Knoten</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">11</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">9.5</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">10</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">9</td>
    </tr>
    <tr class="font-semibold">
      <td class="border border-[var(--border)] px-2 py-1">Schlauche</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono">15</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">10.5</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">12</td>
      <td class="border border-[var(--border)] px-2 py-1 text-center font-mono bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300">9</td>
    </tr>
    <tr class="font-bold text-sm">
      <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5">{{ t('protocol.total') }}</td>
      <td class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center font-mono">34</td>
      <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">27.5</td>
      <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300">30</td>
      <td class="border border-[var(--border)] px-2 py-1.5 text-center font-mono bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-300">25</td>
    </tr>
  </tbody>
</template>
