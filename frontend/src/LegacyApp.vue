/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import AppFooter from '@/components/layout/AppFooter.vue'
import LandingHeader from '@/components/layout/LandingHeader.vue'
import ToastContainer from '@/components/feedback/ToastContainer.vue'

const route = useRoute()
const publicRoutes = ['home', 'login', 'forgot-password', 'set-password', 'reset-password', 'apply', 'apply-verify', 'style', 'station-select', 'privacy', 'terms', 'imprint', 'reconsent', 'patch-notes', 'public-discovery']
const isPublic = computed(() => publicRoutes.includes(route.name as string))
</script>

<template>
  <template v-if="isPublic">
    <div class="flex min-h-screen flex-col">
      <LandingHeader/>
      <main class="flex-1">
        <RouterView/>
      </main>
      <AppFooter/>
    </div>
  </template>
  <RouterView v-else/>
  <ToastContainer />
</template>
