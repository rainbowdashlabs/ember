/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useRouter} from 'vue-router'

const router = useRouter()

/** The account opens on the profile, which is the first thing its sidebar offers and the first thing anybody comes here for. */
onMounted(() => {
  router.replace({name: 'account-avatar'})
})
</script>

<template>
  <div/>
</template>
