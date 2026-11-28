/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import {useSession} from '@/composables/useSession'
import ShopSection from './lendingshopview/ShopSection.vue'

/** The lending shop: every partner's gear as products, by days, search and station. */
const {t} = useI18n()
const {loaded} = useSession()
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-shop.title')" :subtitle="t('pages.inventory-lending-shop.subtitle')">
    <ShopSection v-if="loaded"/>
  </ViewContent>
</template>
