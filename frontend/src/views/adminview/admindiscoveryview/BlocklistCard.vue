/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {BlocklistKind, DiscoveryBlocklistEntry} from '@/api/discovery'

defineProps<{
  blocklist: DiscoveryBlocklistEntry[]
  value: string
  kind: BlocklistKind
  note: string
}>()

const emit = defineEmits<{
  'update:value': [v: string]
  'update:kind': [v: BlocklistKind]
  'update:note': [v: string]
  add: []
  remove: [entry: DiscoveryBlocklistEntry]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminDiscovery.blocklist') }}</SubHeader>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
      <TextInput :model-value="value" :placeholder="t('adminDiscovery.blocklistValue')"
                 @update:model-value="(v) => emit('update:value', v as string)"/>
      <SelectInput :model-value="kind"
                   @update:model-value="(v) => emit('update:kind', v as BlocklistKind)">
        <option value="BASE_URL">{{ t('adminDiscovery.blocklistKindBaseUrl') }}</option>
        <option value="PUBLIC_KEY">{{ t('adminDiscovery.blocklistKindPublicKey') }}</option>
      </SelectInput>
      <TextInput :model-value="note" :placeholder="t('adminDiscovery.blocklistNote')"
                 @update:model-value="(v) => emit('update:note', v as string)"/>
    </div>
    <PrimaryButton :disabled="!value.trim()" @click="emit('add')">
      {{ t('adminDiscovery.blocklistAdd') }}
    </PrimaryButton>
    <EmptyState v-if="blocklist.length === 0">{{ t('adminDiscovery.blocklistEmpty') }}</EmptyState>
    <ul v-else class="divide-y divide-bg-light-accent dark:divide-bg-dark-accent">
      <li v-for="entry in blocklist" :key="entry.value" class="flex items-center justify-between gap-3 py-2">
        <div class="min-w-0">
          <p class="text-sm font-medium break-all">{{ entry.value }}</p>
          <p class="text-xs text-(--text-muted)">
            <span>{{
                entry.kind === 'BASE_URL'
                    ? t('adminDiscovery.blocklistKindBaseUrl')
                    : t('adminDiscovery.blocklistKindPublicKey')
              }}</span>
            <span v-if="entry.note"> · {{ entry.note }}</span>
          </p>
        </div>
        <ErrorButton @click="emit('remove', entry)">
          {{ t('adminDiscovery.blocklistRemove') }}
        </ErrorButton>
      </li>
    </ul>
  </NeutralContainer>
</template>
