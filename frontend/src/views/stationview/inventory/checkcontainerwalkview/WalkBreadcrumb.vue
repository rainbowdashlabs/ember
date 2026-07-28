/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'

defineProps<{
  pathDisplay: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center flex-wrap gap-2 mb-3 text-sm text-(--text-muted)">
    <router-link :to="{name: 'inventory-check-container-overview'}" class="hover:underline">
      {{ t('inventory.checkContainer.title') }}
    </router-link>
    <span>/</span>
    <span class="text-(--text)">{{ pathDisplay }}</span>
  </div>
</template>
