/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'

/** The search over every listed station, local and remote, which filters the map and the list alike. */
const search = defineModel<string>('search', {required: true})

const {t} = useI18n()
</script>

<template>
  <SearchInput v-model="search" :placeholder="t('discovery.searchPlaceholder')"/>
</template>
