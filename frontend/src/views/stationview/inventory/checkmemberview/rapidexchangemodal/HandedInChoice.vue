/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * Whether the piece was handed over there and then.
 *
 * <p>Nothing is chosen to begin with, because this is a question and not a default: it decides how
 * far the exchange has already come, and the answer is only known by whoever is standing there.
 */
const handedIn = defineModel<boolean | null>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('inventory.check.exchangeHandedIn') }}</FieldLabel>
    <div class="flex flex-wrap gap-2">
      <SecondaryButton
          :class="{'ring-2 ring-(--accent)': handedIn === true}"
          data-testid="rapid-exchange-handed-in"
          @click="handedIn = true"
      >
        {{ t('inventory.check.exchangeHandedInYes') }}
      </SecondaryButton>
      <SecondaryButton
          :class="{'ring-2 ring-(--accent)': handedIn === false}"
          data-testid="rapid-exchange-kept"
          @click="handedIn = false"
      >
        {{ t('inventory.check.exchangeHandedInNo') }}
      </SecondaryButton>
    </div>
  </div>
</template>
