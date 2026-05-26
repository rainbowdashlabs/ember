/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watchEffect} from 'vue'
import {useRoute} from 'vue-router'

import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import type {ThemeColors} from '@/theme/themes'

const route = useRoute()
const hasCustomParam = ref(false)

const samplePatch = `--- a/notes.md
+++ b/notes.md
@@ -1,5 +1,6 @@
 # Meeting Notes
-Date: 2026-05-20
+Date: 2026-05-26
+Status: Final

-Action items are pending.
+All action items completed.
 Next meeting in two weeks.`

function applyCustomColors(colors: ThemeColors) {
  const root = document.documentElement.style
  root.setProperty('--color-primary', colors.primary)
  root.setProperty('--color-primary-accent', colors.primaryAccent)
  root.setProperty('--color-secondary', colors.secondary)
  root.setProperty('--color-secondary-accent', colors.secondaryAccent)
  root.setProperty('--color-info', colors.info)
  root.setProperty('--color-info-accent', colors.infoAccent)
  root.setProperty('--color-success', colors.success)
  root.setProperty('--color-error', colors.error)
  root.setProperty('--color-bg-light', colors.bgLight)
  root.setProperty('--color-bg-light-accent', colors.bgLightAccent)
  root.setProperty('--color-bg-dark', colors.bgDark)
  root.setProperty('--color-bg-dark-accent', colors.bgDarkAccent)
}

// Apply custom colors from query param — use watchEffect with post flush
// to ensure it runs after the theme system (initFromLocalStorage, fetchPublicTheme)
watchEffect(() => {
  const param = route.query.customTheme as string | undefined
  if (param) {
    try {
      const colors = JSON.parse(decodeURIComponent(param)) as ThemeColors
      hasCustomParam.value = true
      // Delay slightly to win the race against fetchPublicTheme
      setTimeout(() => applyCustomColors(colors), 50)
    } catch { /* ignore */ }
  }
}, { flush: 'post' })

import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SecondaryContainer from '@/components/container/SecondaryContainer.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DiffView from '@/components/display/DiffView.vue'

import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import UploadButton from '@/components/button/UploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmButton from '@/components/button/ConfirmButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'

import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'

import StyleTypography from '@/views/styleview/StyleTypography.vue'
import StyleInputs from '@/views/styleview/StyleInputs.vue'
import StyleFeedback from '@/views/styleview/StyleFeedback.vue'

const toggleStates = ref(new Set([1, 3]))
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-6 sm:px-8 sm:py-8 space-y-10 sm:space-y-12">
    <!-- Theme Picker at top -->
    <div class="space-y-4">
      <PageHeader>Style Guide</PageHeader>
      <ThemeSelector/>
    </div>

    <StyleTypography/>

    <!-- Buttons -->
    <section class="space-y-4">
      <SectionHeader>Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton>Primary</PrimaryButton>
        <SecondaryButton>Secondary</SecondaryButton>
        <SuccessButton>Success</SuccessButton>
        <ErrorButton>Error</ErrorButton>
        <InfoButton>Info</InfoButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton :icon="['fas', 'plus']">With Icon</PrimaryButton>
        <SecondaryButton :icon="['fas', 'download']">Download</SecondaryButton>
        <SuccessButton :icon="['fas', 'check']">Confirm</SuccessButton>
        <ErrorButton :icon="['fas', 'trash']">Delete</ErrorButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton compact>Compact</PrimaryButton>
        <SecondaryButton compact>Compact</SecondaryButton>
        <SuccessButton compact>Compact</SuccessButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryButton disabled>Disabled</PrimaryButton>
        <SecondaryButton disabled>Disabled</SecondaryButton>
        <SuccessButton disabled>Disabled</SuccessButton>
        <ErrorButton disabled>Disabled</ErrorButton>
        <InfoButton disabled>Disabled</InfoButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <LinkButton>Link Button</LinkButton>
        <LinkButton disabled>Link (disabled)</LinkButton>
      </div>
    </section>

    <!-- Icon Buttons -->
    <section class="space-y-4">
      <SectionHeader>Icon Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <IconButton :icon="['fas', 'gear']" label="Settings"/>
        <DownloadButton/>
        <UploadButton/>
        <DeleteButton/>
        <ConfirmButton/>
        <EditButton/>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <IconButton :icon="['fas', 'gear']" label="Settings" disabled/>
        <DownloadButton disabled/>
        <UploadButton disabled/>
        <DeleteButton disabled/>
        <ConfirmButton disabled/>
        <EditButton disabled/>
      </div>
    </section>

    <!-- Selection Toggle & Dropdown -->
    <section class="space-y-4">
      <SectionHeader>Selection Toggle Buttons</SectionHeader>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton
            v-for="i in 5"
            :key="i"
            :selected="toggleStates.has(i)"
            @toggle="toggleStates.has(i) ? toggleStates.delete(i) : toggleStates.add(i)"
        >
          Option {{ i }}
        </SelectionToggleButton>
      </div>
    </section>

    <section class="space-y-4">
      <SectionHeader>Dropdown Menu Items</SectionHeader>
      <NeutralContainer class="p-0! max-w-xs overflow-hidden">
        <DropdownMenuItem :icon="['fas', 'folder']" icon-class="text-(--accent)">Neuer Ordner</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'file-lines']">Neue Datei</DropdownMenuItem>
        <DropdownMenuItem :icon="['fas', 'upload']">Hochladen</DropdownMenuItem>
        <DropdownMenuItem :icon="['fab', 'youtube']" icon-class="text-red-600">YouTube</DropdownMenuItem>
      </NeutralContainer>
    </section>

    <StyleInputs/>

    <!-- Badges -->
    <section class="space-y-4">
      <SectionHeader>Badges</SectionHeader>
      <div class="flex flex-wrap gap-2 items-center">
        <PrimaryBadge>Primary</PrimaryBadge>
        <SecondaryBadge>Secondary</SecondaryBadge>
        <SuccessBadge>Success</SuccessBadge>
        <ErrorBadge>Error</ErrorBadge>
        <InfoBadge>Info</InfoBadge>
        <SizeBadge>M</SizeBadge>
        <SizeBadge lost>M (lost)</SizeBadge>
      </div>
    </section>

    <!-- Containers -->
    <section class="space-y-4">
      <SectionHeader>Containers</SectionHeader>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <PrimaryContainer>Primary container</PrimaryContainer>
        <SecondaryContainer>Secondary container</SecondaryContainer>
        <SuccessContainer>Success container</SuccessContainer>
        <ErrorContainer>Error container</ErrorContainer>
        <InfoContainer>Info container</InfoContainer>
        <NeutralContainer>Neutral container</NeutralContainer>
      </div>
    </section>

    <!-- Table -->
    <section class="space-y-4">
      <SectionHeader>Table</SectionHeader>
      <NeutralContainer :padded="false" class="overflow-x-auto">
        <table class="w-full text-sm">
          <THead>
            <Th>Name</Th>
            <Th>Role</Th>
            <Th align="right">Score</Th>
          </THead>
          <tbody>
            <TRow>
              <Td>Alice</Td>
              <Td>Admin</Td>
              <Td align="right">95</Td>
            </TRow>
            <TRow>
              <Td>Bob</Td>
              <Td muted>Member</Td>
              <Td align="right">72</Td>
            </TRow>
            <TRow>
              <Td>Charlie</Td>
              <Td muted>Guest</Td>
              <Td align="right" muted>—</Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </section>

    <section>
      <SectionHeader>Diff View</SectionHeader>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <NeutralContainer class="!p-0 overflow-hidden">
          <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">Compact (no line numbers)</p>
          <DiffView :patch="samplePatch" compact/>
        </NeutralContainer>
        <NeutralContainer class="!p-0 overflow-hidden">
          <p class="px-3 py-2 text-sm font-medium border-b border-(--border)">With line numbers</p>
          <DiffView :patch="samplePatch" show-line-numbers/>
        </NeutralContainer>
      </div>
    </section>

    <StyleFeedback/>
  </div>
</template>
