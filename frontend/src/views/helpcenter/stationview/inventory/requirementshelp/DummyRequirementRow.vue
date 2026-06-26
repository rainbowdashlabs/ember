/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  name: string
  quantity: number
}>()
</script>

<template>
  <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
    <MutedIcon size="md" :icon="['fas', 'grip-vertical']" />
    <div class="text-sm">{{ name }}</div>
    <div class="text-sm text-center font-medium">{{ quantity }}</div>
    <DeleteButton />
  </div>
</template>
