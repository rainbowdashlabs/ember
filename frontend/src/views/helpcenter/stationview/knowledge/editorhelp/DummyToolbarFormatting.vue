/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import KeyBadge from '@/components/display/KeyBadge.vue'
import DummyToolbarRow from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbarRow.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <DummyToolbarRow :label="t('helpCenter.knowledgeEditorToolbarFormatting.rowLabel')">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'bold']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarFormatting.bold') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'italic']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarFormatting.italic') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'underline']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarFormatting.underline') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'strikethrough']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarFormatting.strikethrough') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'code']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarFormatting.code') }}
    </KeyBadge>
  </DummyToolbarRow>
</template>
