/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import {useSession} from '@/composables/useSession'
import CatalogSection from './lendingcatalogview/CatalogSection.vue'

/** The lending catalog: every partner's gear as products, by days, search and station. */
const {t} = useI18n()
const {loaded} = useSession()
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-catalog.title')" :subtitle="t('pages.inventory-lending-catalog.subtitle')">
    <CatalogSection v-if="loaded"/>
  </ViewContent>
</template>
