/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import UploadButton from '@/components/button/UploadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ConfirmButton from '@/components/button/ConfirmButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StyleButtonsRows from '@/views/styleview/StyleButtonsRows.vue'

const toggleStates = ref(new Set([1, 3]))
</script>

<template>
  <section class="space-y-4">
    <SubHeader>Buttons</SubHeader>
    <StyleButtonsRows/>
  </section>

  <section class="space-y-4">
    <SectionHeader>Icon Buttons</SectionHeader>
    <div class="flex flex-wrap gap-2 items-center"><IconButton :icon="['fas', 'gear']" label="Settings"/><DownloadButton/><UploadButton/><DeleteButton/><ConfirmButton/><EditButton/></div>
    <div class="flex flex-wrap gap-2 items-center"><IconButton :icon="['fas', 'gear']" label="Settings" disabled/><DownloadButton disabled/><UploadButton disabled/><DeleteButton disabled/><ConfirmButton disabled/><EditButton disabled/></div>
  </section>

  <section class="space-y-4">
    <SectionHeader>Muted Icon Buttons</SectionHeader>
    <div class="flex flex-wrap gap-2 items-center">
      <MutedIconButton :icon="['fas', 'pen']" label="Edit" hover="primary"/>
      <MutedIconButton :icon="['fas', 'xmark']" label="Remove" hover="error"/>
      <MutedIconButton :icon="['fas', 'eye']" label="View" hover="text"/>
    </div>
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
      <DropdownMenuItem :icon="['fas', 'trash']" destructive>Löschen</DropdownMenuItem>
    </NeutralContainer>
  </section>
</template>
