/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import MutedText from '@/components/typography/MutedText.vue'

defineProps<{
  activeView: string
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <NeutralContainer class="space-y-3">
      <div class="flex items-start justify-between gap-3">
        <div>
          <SubHeader>Dienstplanänderung Mai</SubHeader>
          <MutedText tag="p">Admin &middot; 14.05.2026, 10:30</MutedText>
        </div>
        <div v-if="activeView === 'manager'" class="flex items-center gap-1 shrink-0">
          <EditButton/>
          <DeleteButton/>
        </div>
      </div>
      <ProseContent>
        <p>Ab dem 20. Mai gilt ein neuer Dienstplan. Bitte prüft eure Einteilungen.</p>
      </ProseContent>
      <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'comment']" class="h-3.5 w-3.5"/>
          {{ t('news.commentsCount', {count: 3}) }}
          <font-awesome-icon :icon="['fas', 'chevron-down']" class="h-2.5 w-2.5 ml-0.5"/>
        </SecondaryButton>
      </div>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <div class="flex items-start justify-between gap-3">
        <div>
          <SubHeader>Neue Ausrüstung eingetroffen</SubHeader>
          <MutedText tag="p">Admin &middot; 10.05.2026, 14:00</MutedText>
        </div>
        <div v-if="activeView === 'manager'" class="flex items-center gap-1 shrink-0">
          <EditButton/>
          <DeleteButton/>
        </div>
      </div>
      <ProseContent>
        <p>Die bestellten Helme und Jacken sind da. Bitte meldet euch bei eurem Betreuer zur Ausgabe.</p>
      </ProseContent>
      <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'comment']" class="h-3.5 w-3.5"/>
          {{ t('news.addComment') }}
          <font-awesome-icon :icon="['fas', 'chevron-down']" class="h-2.5 w-2.5 ml-0.5"/>
        </SecondaryButton>
      </div>
    </NeutralContainer>

    <NeutralContainer class="space-y-3">
      <div class="flex items-start justify-between gap-3">
        <div>
          <SubHeader class="flex items-center gap-1">
            {{ t('helpCenter.newsList.federatedTitle') }}
            <StationBadge station-name="JF Partnerwache" class="ml-1"/>
          </SubHeader>
          <MutedText tag="p">08.05.2026, 09:00</MutedText>
        </div>
      </div>
      <ProseContent>
        <p>{{ t('helpCenter.newsList.federatedContent') }}</p>
      </ProseContent>
    </NeutralContainer>
  </div>
</template>
