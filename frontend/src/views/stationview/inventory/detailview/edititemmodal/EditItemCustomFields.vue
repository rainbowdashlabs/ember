/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import CustomFieldsSection from '../CustomFieldsSection.vue'
import type {InventoryFieldDefinition} from '@/api/generated/schema'

defineProps<{
  defs: InventoryFieldDefinition[]
}>()

const values = defineModel<Record<string, unknown>>({default: () => ({})})

const {t} = useI18n()
</script>

<template>
  <div v-if="defs.length > 0" class="space-y-2 pt-2">
    <SubHeader>{{ t('inventory.fields.title') }}</SubHeader>
    <CustomFieldsSection v-model="values" :defs="defs"/>
  </div>
</template>
