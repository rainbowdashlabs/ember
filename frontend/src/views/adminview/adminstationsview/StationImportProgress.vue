/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ImportProgressChecklist from '@/components/transfer/ImportProgressChecklist.vue'
import type {ImportProgress} from '@/api/transfer'

defineProps<{
  progress: ImportProgress
}>()
</script>

<template>
  <ImportProgressChecklist :progress="progress"/>
</template>
