/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
  label: string
}>()
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <span class="font-medium w-28">{{ label }}</span>
    <slot />
  </div>
</template>
