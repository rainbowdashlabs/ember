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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceConfig.title')" :subtitle="t('helpCenter.attendanceConfig.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceConfig.whatIs')">
      <p>{{ t('helpCenter.attendanceConfig.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceConfig.createTitle')">
      <p>{{ t('helpCenter.attendanceConfig.createName') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.createGroups') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.createFields') }}</p>
    </HelpSection>

    <!-- Dummy: Create button and template list -->
    <div class="space-y-4">
      <PrimaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
        {{ t('attendanceConfig.create') }}
      </PrimaryButton>

      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <NeutralContainer class="flex items-center justify-between">
          <span class="font-medium">Übungsabend</span>
          <div class="flex items-center gap-2">
            <IconButton :icon="['fas', 'copy']" :label="t('attendanceConfig.duplicate')"
                        class="text-secondary hover:bg-secondary/15" disabled/>
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <span class="font-medium">Jugenddienst</span>
          <div class="flex items-center gap-2">
            <IconButton :icon="['fas', 'copy']" :label="t('attendanceConfig.duplicate')"
                        class="text-secondary hover:bg-secondary/15" disabled/>
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between">
          <span class="font-medium">Wettkampf</span>
          <div class="flex items-center gap-2">
            <IconButton :icon="['fas', 'copy']" :label="t('attendanceConfig.duplicate')"
                        class="text-secondary hover:bg-secondary/15" disabled/>
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.attendanceConfig.fieldsTitle')">
      <p>{{ t('helpCenter.attendanceConfig.fieldsText') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldText') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldNumber') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldBoolean') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldEnum') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldDate') }}</p>
      <p>{{ t('helpCenter.attendanceConfig.fieldsOptions') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
