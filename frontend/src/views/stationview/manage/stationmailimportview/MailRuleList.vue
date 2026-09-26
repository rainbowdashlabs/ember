/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DragList from '@/components/input/DragList.vue'
import MailRuleRow from './MailRuleRow.vue'
import MailRuleEditor from './MailRuleEditor.vue'
import {mailImport} from '@/api'
import type {MailRule, MailRuleRequest} from '@/api/mailImport'
import {moveWithin} from '@/util/reorder'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The rules under one mailbox, in the order they are applied.
 *
 * <p>The order is part of what a rule means: the first one that matches takes the message and the others
 * do not run, so it is the reader's to choose and goes through the shared reorder list.
 */
const props = defineProps<{
  mailboxId: number
  supportedTypes: string[]
}>()

const emit = defineEmits<{
  error: [failure: Failure]
}>()

const {t} = useI18n()

const rules = ref<MailRule[]>([])
const editing = ref<MailRule | null>(null)
const adding = ref(false)

/** Hands the failure up, with a sentence of its own where this screen has a better one. */
function record(e: unknown, message?: string) {
  const described = describeFailure(e, t)
  emit('error', message ? {...described, message} : described)
}

/**
 * Fetches the rules, and says so when it cannot.
 *
 * <p>It used to empty the list on a failure, which reads on screen as a mailbox with no rules at
 * all. A reader then writes the rules again, over the ones that are still there.
 */
async function reload(staleMessage?: string) {
  try {
    rules.value = await mailImport.listRules(props.mailboxId)
  } catch (e) {
    record(e, staleMessage)
  }
}

/**
 * Carries the change out, then refreshes the list.
 *
 * <p>The refresh is answered for separately: a rule that was saved and a list that then failed to
 * come back are not the same news, and saying the first failed invites the reader to save it twice.
 */
async function act(action: Promise<unknown>) {
  try {
    await action
  } catch (e) {
    record(e)
    return false
  }
  await reload(t('failure.staleAfterAction'))
  return true
}

async function save(request: MailRuleRequest) {
  const existing = editing.value
  const saved = await act(existing
      ? mailImport.updateRule(existing.id, request)
      : mailImport.createRule(props.mailboxId, request))
  if (saved) {
    editing.value = null
    adding.value = false
  }
}

async function reorder(from: number, to: number) {
  const moved = moveWithin(rules.value, from, to)
  rules.value = moved
  await Promise.all(moved.map((rule, index) => rule.position === index
      ? Promise.resolve()
      : mailImport.updateRule(rule.id, {...rule, position: index})))
  await reload()
}

reload()
</script>

<template>
  <div class="space-y-2 border-t border-[var(--border)] pt-3">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader class="!mb-0">{{ t('mailImport.rules') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" data-testid="rule-add" @click="editing = null; adding = true">
        {{ t('mailImport.addRule') }}
      </SecondaryButton>
    </div>

    <MutedText v-if="rules.length === 0 && !adding" size="sm">{{ t('mailImport.noRules') }}</MutedText>

    <DragList v-else-if="rules.length > 0" :items="rules" :key-fn="(rule) => rule.id" @reorder="reorder">
      <template #default="{item}">
        <MailRuleRow
            :rule="item"
            @edit="editing = item; adding = false"
            @delete="act(mailImport.deleteRule(item.id))"
        />
      </template>
    </DragList>

    <MailRuleEditor
        v-if="adding || editing"
        :rule="editing"
        :supported-types="supportedTypes"
        :position="rules.length"
        @cancel="editing = null; adding = false"
        @save="save"
    />
  </div>
</template>
