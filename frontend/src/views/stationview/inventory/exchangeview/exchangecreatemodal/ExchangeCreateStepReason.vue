/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type { InventorySize } from '@/api/inventory'
import type { MemberItemOption } from './ExchangeCreateStepItem.vue'

const { t } = useI18n()

const reason = defineModel<string>({ required: true })

defineProps<{
  selectedItem: MemberItemOption | null
  newSizeId: string
  sizes: InventorySize[]
  saving: boolean
  canSubmit: boolean
}>()

const emit = defineEmits<{
  submit: []
  back: []
}>()
</script>

<template>
  <p v-if="selectedItem" class="text-sm">
    {{ selectedItem.inventoryName }} — {{ selectedItem.name }}
    <span class="text-(--text-muted)">{{ selectedItem.sizeName ?? t('common.unisize') }}</span>
    <template v-if="newSizeId">
      &rarr; <span class="font-medium">{{ sizes.find(s => s.id === Number(newSizeId))?.label }}</span>
    </template>
  </p>
  <div class="space-y-1">
    <FieldLabel>{{ t('exchanges.reason') }}</FieldLabel>
    <TextAreaInput v-model="reason" :placeholder="t('exchanges.reasonPlaceholder')" />
  </div>
  <div class="flex justify-between">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :disabled="saving || !canSubmit" @click="emit('submit')">
      {{ saving ? t('common.loading') : t('exchanges.submit') }}
    </PrimaryButton>
  </div>
</template>
