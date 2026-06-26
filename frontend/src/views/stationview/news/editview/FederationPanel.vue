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
import FederationSharePicker from '@/components/input/FederationSharePicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {PartnerResponse} from '@/api/federation'

const shared = defineModel<boolean>('shared', {required: true})
const scope = defineModel<string>('scope', {required: true})
const partnerIds = defineModel<number[]>('partnerIds', {required: true})
const visibilityRole = defineModel<string>('visibilityRole', {required: true})

const props = defineProps<{
  partners: PartnerResponse[]
  canFederate: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('news.federation') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('news.federationShareHint') }}</p>
    <FederationSharePicker
        :shared="shared"
        :scope="scope"
        :partner-ids="partnerIds"
        :partners="props.partners"
        :disabled="!props.canFederate"
        :no-permission-hint="t('news.federationNoPermission')"
        @update:shared="shared = $event"
        @update:scope="scope = $event"
        @update:partner-ids="partnerIds = $event"
    />
    <template v-if="shared && props.canFederate">
      <div class="space-y-1">
        <FieldLabel>{{ t('news.federationVisibility') }}</FieldLabel>
        <SelectInput v-model="visibilityRole">
          <option value="MEMBER">{{ t('news.visibilityAllMembers') }}</option>
          <option value="TEAM">{{ t('news.visibilityTeam') }}</option>
          <option value="MANAGER">{{ t('news.visibilityManager') }}</option>
        </SelectInput>
      </div>
    </template>
  </NeutralContainer>
</template>
