/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import GradingSheetExampleSection from './gradinghelp/GradingSheetExampleSection.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.protocolGrading.title')" :subtitle="t('helpCenter.protocolGrading.subtitle')">
    <HelpSection :title="t('helpCenter.protocolGrading.whatIs')">
      <p>{{ t('helpCenter.protocolGrading.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.howTo')">
      <p>{{ t('helpCenter.protocolGrading.step1') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step2') }}</p>
      <p>{{ t('helpCenter.protocolGrading.step3') }}</p>
    </HelpSection>

    <GradingSheetExampleSection />

    <HelpSection :title="t('helpCenter.protocolGrading.sectionsTitle')">
      <p>{{ t('helpCenter.protocolGrading.sectionsText') }}</p>
      <p>{{ t('helpCenter.protocolGrading.openSectionsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.checkTitle')">
      <p>{{ t('helpCenter.protocolGrading.checkText') }}</p>
      <p>{{ t('helpCenter.protocolGrading.bonusText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.plannedTitle')">
      <p>{{ t('helpCenter.protocolGrading.plannedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.navigationTitle')">
      <p>{{ t('helpCenter.protocolGrading.navigationText') }}</p>
      <div class="space-y-2">
        <ButtonRow>
          <SuccessButton class="sm:flex-initial" disabled>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndNext') }}
          </SuccessButton>
          <SuccessButton class="sm:flex-initial" disabled>
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1" /> {{ t('protocol.markDoneAndExit') }}
          </SuccessButton>
        </ButtonRow>
        <ButtonRow>
          <SecondaryButton class="flex-1 sm:flex-initial" disabled>
            <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" /> {{ t('protocol.prevSection') }}
          </SecondaryButton>
          <div class="hidden sm:block flex-1" />
          <PrimaryButton class="sm:flex-initial" disabled>
            {{ t('protocol.nextSection') }} <font-awesome-icon :icon="['fas', 'chevron-right']" class="ml-1" />
          </PrimaryButton>
        </ButtonRow>
      </div>
      <p>{{ t('helpCenter.protocolGrading.finishText') }}</p>
      <ButtonRow align="end">
        <SuccessButton class="flex-1 sm:flex-initial" disabled>
          <font-awesome-icon :icon="['fas', 'flag']" class="mr-1" /> {{ t('protocol.finish') }}
        </SuccessButton>
      </ButtonRow>
    </HelpSection>

    <HelpSection :title="t('helpCenter.protocolGrading.lockTitle')">
      <p>{{ t('helpCenter.protocolGrading.lockText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.protocolGrading.tip') }}</HelpTip>
  </HelpArticle>
</template>
