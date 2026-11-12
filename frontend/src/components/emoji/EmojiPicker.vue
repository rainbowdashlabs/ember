/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import BareButton from '@/components/button/BareButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {emojiCatalog, searchEmoji, type EmojiGroup} from '@/util/emojiCatalog'

/**
 * Every standard emoji, by group and searchable by its German name and keywords. The catalog is
 * fetched when the picker first opens and not before, because it is large and most visits never
 * open it.
 */
defineEmits<{
  pick: [emoji: string]
}>()

const {t} = useI18n()
const groups = ref<EmojiGroup[] | null>(null)
const failed = ref(false)
const search = ref('')

onMounted(() => {
  emojiCatalog().then(found => { groups.value = found }).catch(() => { failed.value = true })
})

const shown = computed<EmojiGroup[]>(() => {
  if (!groups.value) return []
  if (!search.value.trim()) return groups.value
  return [{key: 'search', label: t('messages.emoji.found'), emoji: searchEmoji(groups.value, search.value)}]
})
</script>

<template>
  <div class="w-72 space-y-2 p-2" data-testid="emoji-picker">
    <TextInput v-model="search" :placeholder="t('messages.emoji.search')"/>
    <Spinner v-if="!groups && !failed" size="md"/>
    <EmptyState v-if="failed" compact :message="t('messages.emoji.unavailable')"/>
    <div class="max-h-64 overflow-y-auto">
      <section v-for="group in shown" :key="group.key" class="mb-2">
        <MutedText tag="p" class="mb-1 font-semibold">{{ group.label }}</MutedText>
        <EmptyState v-if="group.emoji.length === 0" compact :message="t('messages.emoji.none')"/>
        <ul class="grid grid-cols-8 gap-0.5">
          <li v-for="entry in group.emoji" :key="entry.unicode">
            <BareButton :title="entry.label" :aria-label="entry.label"
                        class="flex h-8 w-8 items-center justify-center rounded-theme text-xl hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                        @click="$emit('pick', entry.unicode)">{{ entry.unicode }}</BareButton>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>
