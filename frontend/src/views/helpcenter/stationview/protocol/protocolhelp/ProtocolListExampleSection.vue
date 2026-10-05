/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import LocalProtocolRow from '@/views/stationview/protocol/protocollistview/LocalProtocolRow.vue'
import SharedProtocolRow from '@/views/stationview/protocol/protocollistview/SharedProtocolRow.vue'
import type { SharedProtocolView } from '@/api/generated/schema'
import { demoProtocols } from '../fixtures'

/** The protocol list with both own protocols, the first one shared with partners, and one from a partner. */
const { t } = useI18n()
const protocols = demoProtocols(t)
const partnerProtocol: SharedProtocolView = {
  id: 7,
  name: t('helpCenter.sample.protocol.clasp'),
  description: '',
  stationName: t('helpCenter.sample.stations.dlrgNeustadt'),
  stationUid: null,
}
</script>

<template>
  <HelpSection :title="t('helpCenter.protocol.listTitle')">
    <p>{{ t('helpCenter.protocol.listText') }}</p>
    <ButtonRow align="end" class="mb-4">
      <PrimaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.create') }}
      </PrimaryButton>
    </ButtonRow>
    <div class="mb-4">
      <SearchInput :model-value="''" :placeholder="t('protocol.search')" />
    </div>
    <div class="flex flex-wrap items-center gap-2 mb-4">
      <SelectionToggleButton :selected="true" disabled>
        <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
        {{ t('federation.shared') }}
      </SelectionToggleButton>
    </div>
    <div class="space-y-2">
      <LocalProtocolRow
        v-for="(protocol, index) in protocols"
        :key="protocol.id"
        :protocol="protocol"
        can-configure
        can-share
        :shared="index === 0"
        :sharing="false"
      />
      <SharedProtocolRow :shared="partnerProtocol" />
    </div>
  </HelpSection>
</template>
