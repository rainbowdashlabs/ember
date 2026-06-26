/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FederationSharePicker from '@/components/input/FederationSharePicker.vue'
import type {PartnerResponse} from '@/api/federation'

defineProps<{
  partners: PartnerResponse[]
  canFederate: boolean
}>()

const shared = defineModel<boolean>('shared', {required: true})
const scope = defineModel<string>('scope', {required: true})
const partnerIds = defineModel<number[]>('partnerIds', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SubHeader>{{ t('events.federation') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('events.federationShareHint') }}</p>
    <FederationSharePicker
        v-model:shared="shared"
        v-model:scope="scope"
        v-model:partner-ids="partnerIds"
        :partners="partners"
        :disabled="!canFederate"
        :no-permission-hint="t('events.federationNoPermission')"
    />
  </NeutralContainer>
</template>
