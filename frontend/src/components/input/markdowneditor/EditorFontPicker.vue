/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Editor} from '@tiptap/vue-3'
import type {FontFamilyOption} from '@/api/generated/schema'
import FontChoiceList from '@/components/documents/fonts/FontChoiceList.vue'
import {choiceValue, familyChoices, fontEntries} from '@/components/documents/fonts/fontOptions'
import FloatingPanel from '@/components/feedback/FloatingPanel.vue'
import EditorToolbarButton from './EditorToolbarButton.vue'
import {TEXT_FONT} from './textFont'

/**
 * The font entry of the text editor's menu: sets the selected words in a family the template reaches,
 * or back in the template's own font. Each family is listed with where it comes from and a line of
 * sample text the server draws in it. A family the words name but the template no longer reaches stays
 * listed and marked, as in the page's font pickers.
 *
 * <p>In the template editor the words show in the chosen family once its files are loaded; the button
 * names the family at the cursor either way, which is the only hint where no file could be loaded.
 */
const props = defineProps<{
  editor: Editor | undefined
  fonts: readonly FontFamilyOption[]
}>()

const {t} = useI18n()
const open = ref(false)

const current = computed(() => {
  const family = props.editor?.getAttributes(TEXT_FONT).family
  return typeof family === 'string' && family.length > 0 ? family : null
})

const choices = computed(() => familyChoices(props.fonts, current.value))
const entries = computed(() => fontEntries(choices.value, {name: t('markdownEditor.fontOfTemplate')}))

function choose(value: string) {
  const chain = props.editor?.chain().focus()
  if (value === '') chain?.unsetMark(TEXT_FONT).run()
  else chain?.setMark(TEXT_FONT, {family: value}).run()
  open.value = false
}
</script>

<template>
  <FloatingPanel v-model:open="open" :label="t('markdownEditor.font')" role="dialog" align="start"
                 panel-class="flex max-h-80 w-72 flex-col overflow-hidden">
    <template #trigger="{triggerAttrs}">
      <EditorToolbarButton :icon="['fas', 'font']" :label="t('markdownEditor.font')" :active="!!current" data-testid="editor-font"
                           v-bind="triggerAttrs" @click="open = !open">
        <span v-if="current" class="ml-1 text-xs max-w-32 truncate">{{ current }}</span>
      </EditorToolbarButton>
    </template>
    <FontChoiceList :model-value="choiceValue(choices, current)" :entries="entries" :label="t('markdownEditor.font')"
                    :missing-note="t('markdownEditor.fontMissing')" @update:model-value="choose"/>
  </FloatingPanel>
</template>
