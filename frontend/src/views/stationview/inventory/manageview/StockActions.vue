/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'

/**
 * What can be started from the stock screen.
 *
 * <p>Sending gear out of a store only appears where the routes name it, which is the association's screens
 * and nowhere else: a station holding somebody else's gear has no store to send out of, and its own gear
 * goes to its own people rather than to another station.
 */
const emit = defineEmits<{
  create: []
}>()

const {t} = useI18n()
const router = useRouter()
const routes = useInventoryRoutes()
</script>

<template>
  <div class="flex items-center justify-end gap-2">
    <SecondaryButton
        v-if="routes.dispatch"
        :icon="['fas', 'paper-plane']"
        data-testid="inventory-dispatch-link"
        @click="router.push({name: routes.dispatch})"
    >
      {{ t('inventory.manage.dispatch') }}
    </SecondaryButton>
    <PrimaryButton :icon="['fas', 'plus']" data-testid="create-inventory" @click="emit('create')">
      {{ t('inventory.manage.create') }}
    </PrimaryButton>
  </div>
</template>
