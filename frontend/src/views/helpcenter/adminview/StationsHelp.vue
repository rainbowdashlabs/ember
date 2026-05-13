/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStations.title')" :subtitle="t('helpCenter.adminStations.subtitle')">
    <HelpSection :title="t('helpCenter.adminStations.whatIs')">
      <p>{{ t('helpCenter.adminStations.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.createTitle')">
      <p>{{ t('helpCenter.adminStations.createText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStations.editTitle')">
      <p>{{ t('helpCenter.adminStations.editText') }}</p>
    </HelpSection>

    <!-- Dummy: Station list -->
    <div class="grid gap-4 sm:grid-cols-2">
      <PrimaryContainer
          class="flex flex-col items-center justify-center gap-3 cursor-pointer py-6 border-dashed hover:opacity-80 transition-opacity">
        <font-awesome-icon :icon="['fas', 'plus']" class="text-2xl"/>
        <span class="font-medium">{{ t('adminStations.create') }}</span>
      </PrimaryContainer>

      <NeutralContainer class="flex items-center justify-between py-6">
        <span class="font-medium text-lg">DLRG Musterstadt</span>
        <div class="flex items-center gap-2">
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>

      <NeutralContainer class="flex items-center justify-between py-6">
        <span class="font-medium text-lg">DLRG Beispielburg</span>
        <div class="flex items-center gap-2">
          <EditButton/>
          <DeleteButton/>
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.adminStations.deleteTitle')">
      <p>{{ t('helpCenter.adminStations.deleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStations.tip') }}</HelpTip>
  </HelpArticle>
</template>
