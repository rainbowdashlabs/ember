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
  <DummyToolbarRow :label="t('helpCenter.knowledgeEditorToolbarStructure.rowLabel')">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'heading']" class="w-3 h-3" /><span class="text-[10px] font-bold">1</span>
    </KeyBadge> <span class="text-[10px] font-bold">2</span> <span class="text-[10px] font-bold">3</span> {{ t('helpCenter.knowledgeEditorToolbarStructure.headings') }}
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'list-ul']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarStructure.bulletList') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'list-ol']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarStructure.numberedList') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'quote-left']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarStructure.quote') }}
    </KeyBadge>
  </DummyToolbarRow>
</template>
