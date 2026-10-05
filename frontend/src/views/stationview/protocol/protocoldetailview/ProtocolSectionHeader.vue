/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {TestProtocolSection} from '@/api/generated/schema'
import {useProtocolEditor} from './protocolEditor'

/** The line naming a section of the editor, with what it is worth and what can be done with it. */
const props = defineProps<{
  section: TestProtocolSection
  depth: number
}>()

const {t} = useI18n()
const editor = useProtocolEditor()

const isCut = computed(() => editor.cut.value?.id === props.section.id)
const canPasteHere = computed(() => editor.cut.value !== null && editor.canPasteInto(props.section.id))
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <SubHeader v-if="depth === 0">{{ section.name }}</SubHeader>
    <span v-else class="font-medium text-sm">{{ section.name }}</span>
    <MutedText class="ml-auto">{{ editor.tree.value.maxPointsUnder(section.id) }}P</MutedText>
    <template v-if="editor.canEdit.value">
      <IconButton v-if="canPasteHere" :icon="['fas', 'paste']" :label="t('protocol.pasteInto', {name: section.name})"
                  class="text-[var(--color-primary)]" @click="editor.pasteInto(section.id)"/>
      <IconButton :icon="['fas', 'plus']" :label="t('protocol.addItem')" @click="editor.addItem(section.id)"/>
      <IconButton :icon="['fas', 'folder-plus']" :label="t('protocol.addSubsection')"
                  @click="editor.addSubsection(section.id)"/>
      <IconButton :icon="['fas', 'scissors']" :label="t('protocol.cutSection')" :disabled="isCut"
                  @click="editor.cutSection(section)"/>
      <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="editor.editSection(section)"/>
      <DeleteButton :label="t('common.delete')" @click="editor.deleteSection(section.id)"/>
    </template>
  </div>
</template>
