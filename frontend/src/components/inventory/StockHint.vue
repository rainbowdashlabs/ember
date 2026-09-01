/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldHint from '@/components/typography/FieldHint.vue'
import Alert from '@/components/feedback/Alert.vue'

/**
 * How many pieces there are, and a word where somebody has asked for more of them than exist.
 *
 * <p>The count is what the station has, not what is free on one evening. A line is written for a
 * whole series of evenings, or in a collection for no date at all, so there is no single evening a
 * free count could be taken over; what is free on a given evening stands beside the line in the
 * panel that answers for that evening, with the appointments it collides with named.
 *
 * <p>Asking for too much is reported and never refused. Planning is writing down: two evenings may
 * both want the last trailer, and a dialogue that will not record the second one does not remove the
 * conflict, it hides it until the Saturday.
 */
const props = defineProps<{
  /** How many pieces of the chosen thing the station could bring along at all. */
  stock: number
  /** How many the line asks for. */
  quantity: number
}>()

const {t} = useI18n()

const short = computed(() => props.quantity > props.stock)
</script>

<template>
  <div class="space-y-2">
    <FieldHint data-testid="line-target-stock">
      {{ stock > 0 ? t('inventory.stock.available', {count: stock}) : t('inventory.stock.none') }}
    </FieldHint>
    <Alert v-if="short" variant="error" data-testid="line-target-short">
      {{ t('inventory.stock.short', {requested: quantity, available: stock}) }}
    </Alert>
  </div>
</template>
