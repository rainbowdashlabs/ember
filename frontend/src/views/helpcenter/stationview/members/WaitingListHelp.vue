/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import BulletList from '@/components/typography/BulletList.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import CodeSnippet from '@/components/display/CodeSnippet.vue'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.waitingList.title')" :subtitle="t('helpCenter.waitingList.subtitle')">
    <HelpSection :title="t('helpCenter.waitingList.whatIs')">
      <p>{{ t('helpCenter.waitingList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.waitingList.howTo')">
      <p>{{ t('helpCenter.waitingList.step1') }}</p>
      <p>{{ t('helpCenter.waitingList.step2') }}</p>
      <p>{{ t('helpCenter.waitingList.step3') }}</p>
      <p>{{ t('helpCenter.waitingList.step4') }}</p>
    </HelpSection>

    <!-- Dummy: Waiting list overview -->
    <HelpSection :title="t('helpCenter.waitingList.overviewExampleTitle')">
      <NeutralContainer class="space-y-3">
        <SectionHeader>{{ t('waitingList.title') }}</SectionHeader>
        <NeutralContainer class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-medium">Warteliste 2026</span>
            <SecondaryBadge>3 {{ t('waitingList.entries') }}</SecondaryBadge>
          </div>
          <p class="text-sm text-(--text-muted)">{{ t('helpCenter.waitingList.exampleDesc') }}</p>
        </NeutralContainer>
        <PrimaryButton>{{ t('waitingList.createList') }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <!-- Entry list with status badges -->
    <HelpSection :title="t('helpCenter.waitingList.entriesTitle')">
      <p>{{ t('helpCenter.waitingList.entriesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.waitingList.agesTitle')">
      <p>{{ t('helpCenter.waitingList.agesText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.waitingList.agesField') }}</li>
        <li>{{ t('helpCenter.waitingList.agesRegister') }}</li>
        <li>{{ t('helpCenter.waitingList.agesJoin') }}</li>
      </BulletList>
      <p class="mt-2 text-sm">{{ t('helpCenter.waitingList.agesConvert') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.waitingList.statusTitle')">
      <NeutralContainer class="space-y-2">
        <div class="flex items-center gap-3 flex-wrap">
          <SecondaryBadge>{{ t('waitingList.status_WAITING') }}</SecondaryBadge>
          <InfoBadge>{{ t('waitingList.status_INVITED') }}</InfoBadge>
          <PrimaryBadge>{{ t('waitingList.status_TESTING') }}</PrimaryBadge>
          <SuccessBadge>{{ t('waitingList.status_JOINED') }}</SuccessBadge>
          <ErrorBadge>{{ t('waitingList.status_WITHDRAWN') }}</ErrorBadge>
        </div>
        <p class="text-sm text-(--text-muted)">{{ t('helpCenter.waitingList.statusFlowText') }}</p>
        <p class="text-sm text-(--text-muted)">{{ t('helpCenter.waitingList.statusAccountText') }}</p>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Create entry form -->
    <HelpSection :title="t('helpCenter.waitingList.createEntryTitle')">
      <p>{{ t('helpCenter.waitingList.createEntryText') }}</p>
      <NeutralContainer class="space-y-4 mt-2">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
            <TextInput :model-value="''" :placeholder="t('waitingList.firstnamePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
            <TextInput :model-value="''" :placeholder="t('waitingList.lastnamePlaceholder')"/>
          </div>
        </div>
        <div class="space-y-2">
          <FieldLabel>{{ t('waitingList.guardians') }} <span class="text-error">*</span></FieldLabel>
          <NeutralContainer class="space-y-2">
            <span class="text-sm font-medium">{{ t('waitingList.guardian') }} 1</span>
            <div class="grid gap-2 sm:grid-cols-3">
              <TextInput :model-value="''" :placeholder="t('waitingList.guardianNamePlaceholder')"/>
              <TextInput :model-value="''" :placeholder="t('waitingList.guardianEmailPlaceholder')"/>
              <TextInput :model-value="''" :placeholder="t('waitingList.guardianPhonePlaceholder')"/>
            </div>
          </NeutralContainer>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
          <TextAreaInput :model-value="''" :placeholder="t('waitingList.notesPlaceholder')"/>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Entry detail with status -->
    <HelpSection :title="t('helpCenter.waitingList.entryDetailTitle')">
      <p>{{ t('helpCenter.waitingList.entryDetailText') }}</p>
      <NeutralContainer class="space-y-3 mt-2">
        <div class="flex items-center gap-2">
          <SubHeader>Max Müller</SubHeader>
          <SecondaryBadge>{{ t('waitingList.status_WAITING') }}</SecondaryBadge>
        </div>
        <div class="text-sm text-(--text-muted) flex flex-wrap gap-4">
          <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">9.6</span></span>
          <span>{{ t('waitingList.createdAt') }}: 01.03.2026, 10:00</span>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Field editor -->
    <HelpSection :title="t('helpCenter.waitingList.fieldEditorTitle')">
      <p>{{ t('helpCenter.waitingList.fieldEditorText') }}</p>
      <NeutralContainer class="space-y-3 mt-2">
        <div class="flex items-center justify-between">
          <SubHeader>{{ t('waitingList.fields') }}</SubHeader>
          <PrimaryButton :icon="['fas', 'plus']">{{ t('waitingList.addField') }}</PrimaryButton>
        </div>
        <div class="space-y-2">
          <div class="flex items-center gap-2 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <span class="font-medium">Alter</span>
                <SecondaryBadge>{{ t('waitingList.typeNumber') }}</SecondaryBadge>
                <PrimaryBadge>{{ t('waitingList.required') }}</PrimaryBadge>
              </div>
            </div>
            <EditButton/>
            <DeleteButton/>
          </div>
          <div class="flex items-center gap-2 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30">
            <div class="flex-1">
              <div class="flex items-center gap-2">
                <span class="font-medium">Erfahrung</span>
                <SecondaryBadge>{{ t('waitingList.typeEnum') }}</SecondaryBadge>
              </div>
              <MutedText class="mt-1">{{ t('waitingList.options') }}: Anfänger, Fortgeschritten</MutedText>
            </div>
            <EditButton/>
            <DeleteButton/>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Invites section -->
    <HelpSection :title="t('helpCenter.waitingList.invites')">
      <p>{{ t('helpCenter.waitingList.invitesText') }}</p>
    </HelpSection>

    <!-- Score formula documentation -->
    <HelpSection :title="t('helpCenter.waitingList.scoring')">
      <p>{{ t('helpCenter.waitingList.scoringText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.waitingList.formulaSyntax')">
      <NeutralContainer class="space-y-4">
        <div class="space-y-3 text-sm">
          <div>
            <span class="font-medium">{{ t('helpCenter.waitingList.fieldRef') }}</span>
            <p class="text-(--text-muted)">{{ t('helpCenter.waitingList.fieldRefText') }}</p>
            <CodeSnippet block class="mt-1">[Alter]</CodeSnippet>
          </div>

          <div>
            <span class="font-medium">{{ t('helpCenter.waitingList.examples') }}</span>
            <div class="mt-1 space-y-2">
              <div>
                <CodeSnippet block>[Alter] * 2</CodeSnippet>
                <MutedText tag="p" class="mt-1">{{ t('helpCenter.waitingList.example1') }}</MutedText>
              </div>
              <div>
                <CodeSnippet block>[Alter] &gt; 8 ? [Alter] * 2 : [Alter]</CodeSnippet>
                <MutedText tag="p" class="mt-1">{{ t('helpCenter.waitingList.example3') }}</MutedText>
              </div>
            </div>
          </div>

          <div>
            <span class="font-medium">{{ t('helpCenter.waitingList.generatedFields') }}</span>
            <p class="text-(--text-muted)">{{ t('helpCenter.waitingList.generatedFieldsText') }}</p>
            <div class="mt-1 space-y-1 text-xs">
              <div class="flex items-center gap-2">
                <CodeSnippet>[wartezeit_tage]</CodeSnippet>
                <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.waitDays') }}</span>
              </div>
              <div class="flex items-center gap-2">
                <CodeSnippet>[alter_Geburtstag]</CodeSnippet>
                <span class="text-(--text-muted)">{{ t('helpCenter.waitingList.ageField') }}</span>
              </div>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <!-- Confirmation flow -->
    <HelpSection :title="t('helpCenter.waitingList.confirmation')">
      <p>{{ t('helpCenter.waitingList.confirmationText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.waitingList.tip') }}</HelpTip>
  </HelpArticle>
</template>
