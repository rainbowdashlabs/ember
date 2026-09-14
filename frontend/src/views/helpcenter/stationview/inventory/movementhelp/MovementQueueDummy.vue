/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MovementRowDummy from './MovementRowDummy.vue'
import {MovementPurpose, StepActor} from '@/api/movements'

defineProps<{
  managerView: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SectionHeader>{{ t('movements.queue.title') }}</SectionHeader>
      <ButtonRow>
        <SecondaryButton v-if="managerView" :icon="['fas', 'file-export']">
          {{ t('movements.queue.export') }}
        </SecondaryButton>
        <PrimaryButton :icon="['fas', 'plus']">{{ t('movements.queue.create') }}</PrimaryButton>
      </ButtonRow>
    </div>

    <div class="space-y-2">
      <MovementRowDummy
          :actor="StepActor.STATION"
          :manager-view="managerView"
          :purpose="MovementPurpose.EXCHANGE"
          color="#2563eb"
          date="14.05.2026"
          icon="helmet-safety"
          item="Helm"
          member="Max Mustermann"
          ours
          size-name="M"
          step="Teil zurückgenommen"
      />
      <MovementRowDummy
          :actor="StepActor.OWNER"
          :manager-view="managerView"
          :purpose="MovementPurpose.REQUEST"
          color="#16a34a"
          date="10.05.2026"
          icon="shirt"
          item="Einsatzjacke"
          member="Erika Musterfrau"
          size-name="S"
          step="Beim Verband angefragt"
      />
      <MovementRowDummy
          :actor="StepActor.MEMBER"
          :manager-view="managerView"
          :purpose="MovementPurpose.ISSUE"
          color="#ca8a04"
          date="01.04.2026"
          icon="shoe-prints"
          item="Stiefel"
          member="Jan Schmidt"
          size-name="44"
          step="Auf dem Weg zum Mitglied"
      />
    </div>
  </NeutralContainer>
</template>
