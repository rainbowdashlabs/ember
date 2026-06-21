/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import IdentityButton from '@/components/button/IdentityButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import UploadButton from '@/components/button/UploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmButton from '@/components/button/ConfirmButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'

const toggleStates = ref(new Set([1, 3]))
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Buttons</SectionHeader>
    <div class="flex flex-wrap gap-2 items-center"><PrimaryButton>Primary</PrimaryButton><SecondaryButton>Secondary</SecondaryButton><SuccessButton>Success</SuccessButton><ErrorButton>Error</ErrorButton><InfoButton>Info</InfoButton></div>
    <div class="flex flex-wrap gap-2 items-center"><PrimaryButton :icon="['fas', 'plus']">With Icon</PrimaryButton><SecondaryButton :icon="['fas', 'download']">Download</SecondaryButton><SuccessButton :icon="['fas', 'check']">Confirm</SuccessButton><ErrorButton :icon="['fas', 'trash']">Delete</ErrorButton></div>
    <div class="flex flex-wrap gap-2 items-center"><PrimaryButton compact>Compact</PrimaryButton><SecondaryButton compact>Compact</SecondaryButton><SuccessButton compact>Compact</SuccessButton></div>
    <div class="flex flex-wrap gap-2 items-center"><SaveButton :action="() => new Promise(r => setTimeout(r, 800))"/><SaveButton :action="() => new Promise(r => setTimeout(r, 800))">Custom text</SaveButton></div>
    <div class="flex flex-wrap gap-2 items-center"><PrimaryButton disabled>Disabled</PrimaryButton><SecondaryButton disabled>Disabled</SecondaryButton><SuccessButton disabled>Disabled</SuccessButton><ErrorButton disabled>Disabled</ErrorButton><InfoButton disabled>Disabled</InfoButton></div>
    <div class="flex flex-wrap gap-2 items-center">
      <IdentityButton><font-awesome-icon :icon="['fas', 'user']" class="h-4 w-4"/>Identity</IdentityButton>
      <LinkButton>Link Button</LinkButton>
      <LinkButton disabled>Link (disabled)</LinkButton>
    </div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Icon Buttons</SectionHeader>
    <div class="flex flex-wrap gap-2 items-center"><IconButton :icon="['fas', 'gear']" label="Settings"/><DownloadButton/><UploadButton/><DeleteButton/><ConfirmButton/><EditButton/></div>
    <div class="flex flex-wrap gap-2 items-center"><IconButton :icon="['fas', 'gear']" label="Settings" disabled/><DownloadButton disabled/><UploadButton disabled/><DeleteButton disabled/><ConfirmButton disabled/><EditButton disabled/></div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Selection Toggle Buttons</SectionHeader>
    <div class="flex flex-wrap gap-2">
      <SelectionToggleButton v-for="i in 5" :key="i"
                             :selected="toggleStates.has(i)"
                             @toggle="toggleStates.has(i) ? toggleStates.delete(i) : toggleStates.add(i)">
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
</template>
