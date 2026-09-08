/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {NuxtError} from '#app'
import NotFoundView from '~/views/NotFoundView.vue'

const props = defineProps<{error: NuxtError}>()
</script>

<template>
  <NuxtLayout>
    <NotFoundView :status="props.error.statusCode" />
  </NuxtLayout>
</template>
