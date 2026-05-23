/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useRouter} from 'vue-router'

const router = useRouter()

onMounted(() => {
  router.replace({name: 'inventory-lending'})
})
</script>

<template>
  <div/>
</template>
