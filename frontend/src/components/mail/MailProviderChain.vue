/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import MailProviderRow from '@/components/mail/MailProviderRow.vue'
import {emptyMailProvider, type MailProvider} from '@/api/mailProviders'

/**
 * The order mail is tried through, for whoever owns the list.
 *
 * The same screen serves the instance and a station, because the question is the same one: which
 * provider first, how many attempts and how many mails a day before the next takes over. The first
 * entry is simply the first, not a provider of a different kind, so it is edited here like the
 * rest and can be moved out of the way like the rest.
 */
const props = defineProps<{
  save: () => Promise<unknown>
  /** Whether this list is a station's, which also shows its provider to members. */
  showDisplayFields?: boolean
  /** The address every test field starts with, usually the one of whoever is looking. */
  defaultRecipient?: string
  /**
   * Whether the list on screen is the stored one. Saving before it has arrived would write an
   * empty list over a full one, which is not something anybody comes to this page to do.
   */
  ready?: boolean
}>()

const providers = defineModel<MailProvider[]>('providers', {required: true})

const emit = defineEmits<{
  test: [position: number, recipient: string]
  clear: []
}>()

const {t} = useI18n()

function add() {
  providers.value = [...providers.value, emptyMailProvider()]
}

function replaceAt(index: number, value: MailProvider) {
  providers.value = providers.value.map((entry, i) => (i === index ? value : entry))
}

function remove(index: number) {
  providers.value = providers.value.filter((_, i) => i !== index)
}

function move(index: number, direction: number) {
  const target = index + direction
  if (target < 0 || target >= providers.value.length) return
  const next = [...providers.value]
  const [moved] = next.splice(index, 1)
  if (!moved) return
  next.splice(target, 0, moved)
  providers.value = next
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('mailChain.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('mailChain.hint') }}</MutedText>

    <Spinner v-if="props.ready === false" size="md"/>
    <EmptyHint v-else-if="providers.length === 0">{{ t('mailChain.empty') }}</EmptyHint>

    <MailProviderRow
        v-for="(entry, index) in providers"
        :key="index"
        :model-value="entry"
        @update:model-value="(value: MailProvider) => replaceAt(index, value)"
        :position="index + 1"
        :is-first="index === 0"
        :is-last="index === providers.length - 1"
        :show-display-fields="props.showDisplayFields"
        :default-recipient="props.defaultRecipient"
        @remove="remove(index)"
        @move="(direction: number) => move(index, direction)"
        @test="(recipient: string) => emit('test', index, recipient)"
    >
      <template #webhook="{provider}">
        <slot name="webhook" :provider="provider" :position="index"/>
      </template>
    </MailProviderRow>

    <div class="flex justify-between gap-2 flex-wrap border-t border-(--border) pt-4">
      <div class="flex gap-2">
        <SecondaryButton :icon="['fas', 'plus']" :disabled="props.ready === false" @click="add">
          {{ t('mailChain.add') }}
        </SecondaryButton>
        <ErrorButton
            v-if="providers.length > 0"
            :icon="['fas', 'trash']"
            :disabled="props.ready === false"
            @click="emit('clear')">
          {{ t('mailChain.clearAll') }}
        </ErrorButton>
      </div>
      <SaveButton data-testid="mail-providers-save" :disabled="props.ready === false" :action="props.save"/>
    </div>
  </NeutralContainer>
</template>
