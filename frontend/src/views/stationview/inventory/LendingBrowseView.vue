/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {onMounted} from 'vue'
import {useRouter} from 'vue-router'

const routes = useInventoryRoutes()

const router = useRouter()

onMounted(() => {
  router.replace({name: routes.lending})
})
</script>

<template>
  <div/>
</template>
