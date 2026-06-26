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
  <DummyToolbarRow :label="t('helpCenter.knowledgeEditorToolbarMedia.rowLabel')">
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'link']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarMedia.link') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'table-columns']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarMedia.table') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'highlighter']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarMedia.highlight') }}
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fab', 'youtube']" class="w-3 h-3" /> YouTube
    </KeyBadge>
    <KeyBadge>
      <font-awesome-icon :icon="['fas', 'image']" class="w-3 h-3" /> {{ t('helpCenter.knowledgeEditorToolbarMedia.image') }}
    </KeyBadge>
  </DummyToolbarRow>
</template>
