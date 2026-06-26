/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type {DiscoveryIdentity} from '@/api/discovery'

defineProps<{ identity: DiscoveryIdentity }>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SubHeader>{{ t('adminDiscovery.identity') }}</SubHeader>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
      <div>
        <FieldLabel>{{ t('adminDiscovery.instanceId') }}</FieldLabel>
        <code class="break-all">{{ identity.instanceId }}</code>
      </div>
      <div>
        <FieldLabel>{{ t('adminDiscovery.baseUrl') }}</FieldLabel>
        <code class="break-all">{{ identity.baseUrl }}</code>
      </div>
      <div class="md:col-span-2">
        <FieldLabel>{{ t('adminDiscovery.publicKey') }}</FieldLabel>
        <code class="break-all text-xs">{{ identity.publicKey }}</code>
      </div>
    </div>
  </NeutralContainer>
</template>
