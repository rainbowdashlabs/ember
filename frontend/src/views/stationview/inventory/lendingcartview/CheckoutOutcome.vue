/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import type {CheckoutView} from '@/api/generated/schema'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useRouter} from 'vue-router'

/** What sending the cart did: the requests opened, with links, and the stations whose lines stayed. */
defineProps<{
  outcome: CheckoutView
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()
const router = useRouter()

function requestHref(id: number): string {
  return routes.lendingRequest ? router.resolve({name: routes.lendingRequest, params: {id}}).href : ''
}
</script>

<template>
  <div class="space-y-3" data-testid="lending-cart-outcome">
    <Alert v-if="outcome.sent.length > 0" variant="success">
      <p>{{ outcome.sent.length === 1 ? t('lendingCartPage.sentOne') : t('lendingCartPage.sent', {count: outcome.sent.length}) }}</p>
      <ul class="mt-1 list-disc pl-5">
        <li v-for="sent in outcome.sent" :key="sent.request.id">
          <AppLink :href="requestHref(sent.request.id)">{{ sent.owningStationName }}: {{ sent.itemSummary }}</AppLink>
        </li>
      </ul>
    </Alert>
    <Alert v-if="outcome.notesMissing.length > 0" variant="info">
      {{ t('lendingCartPage.notesMissing', {stations: outcome.notesMissing.join(', ')}) }}
    </Alert>
    <Alert v-if="outcome.failed.length > 0" variant="error">
      {{ t('lendingCartPage.failed', {stations: outcome.failed.join(', ')}) }}
    </Alert>
  </div>
</template>
