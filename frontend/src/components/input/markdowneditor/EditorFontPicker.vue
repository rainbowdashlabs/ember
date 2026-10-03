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
import {TEXT_FONT} from './textFont'

/**
 * The font entry of the text editor's menu: sets the selected words in a family the template reaches,
 * or back in the template's own font. Each family is listed with where it comes from and a line of
 * sample text the server draws in it. A family the words name but the template no longer reaches stays
 * listed and marked, as in the page's font pickers.
 *
 * <p>The font files never reach the browser, so the words keep the editor's look; the button names the
 * family at the cursor instead.
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
      <button type="button" :title="t('markdownEditor.font')" data-testid="editor-font" v-bind="triggerAttrs"
              :class="['p-1.5 rounded text-sm transition-colors inline-flex items-center gap-1', current ? 'text-primary bg-primary/10' : 'text-[var(--text)] hover:bg-[var(--bg-accent)]']"
              @mousedown.prevent @click="open = !open">
        <font-awesome-icon :icon="['fas', 'font']" class="w-3.5 h-3.5"/>
        <span v-if="current" class="text-xs max-w-32 truncate">{{ current }}</span>
      </button>
    </template>
    <FontChoiceList :model-value="choiceValue(choices, current)" :entries="entries" :label="t('markdownEditor.font')"
                    :missing-note="t('markdownEditor.fontMissing')" @update:model-value="choose"/>
  </FloatingPanel>
</template>
