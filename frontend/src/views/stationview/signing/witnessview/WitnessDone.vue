/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * The end of one signature collected in person: it is filed with the person, and their copy goes to them
 * or their guardians. Where the same document still waits on somebody, such as a guardian after the child,
 * that comes first; where other signatures still wait on the person the documents are about, it offers to
 * go on with them; and it always offers the next person.
 */
defineProps<{
  /** Who just signed. */
  name: string
  /** Whom the documents are about, which is who just signed or the child a guardian signed for. */
  personName: string
  /** Who signs the next open field of the same document, empty where nothing more waits on it. */
  followingNames?: string[]
  /** Whether other signatures still wait on the person the documents are about. */
  moreForPerson?: boolean
}>()

defineEmits<{samePerson: []; nextPerson: []; continueDocument: []}>()

const {t} = useI18n()
const headingId = useId()

onMounted(() => document.getElementById(headingId)?.focus())
</script>

<template>
  <SuccessContainer class="space-y-3 max-w-3xl" data-testid="witness-done">
    <SectionHeader :id="headingId" tabindex="-1" class="focus:outline-none">{{ t('signing.witness.done.heading', {name}) }}</SectionHeader>
    <p>{{ t('signing.witness.done.hint', {name}) }}</p>
    <p v-if="followingNames?.length" data-testid="witness-following">
      {{ t('signing.witness.done.following', {names: followingNames.join(t('signing.witness.done.or'))}) }}
    </p>
    <ButtonRow>
      <PrimaryButton v-if="followingNames?.length" type="button" data-testid="witness-continue"
                     @click="$emit('continueDocument')">
        {{ t('signing.witness.done.continue', {names: followingNames.join(t('signing.witness.done.or'))}) }}
      </PrimaryButton>
      <PrimaryButton v-else type="button" data-testid="witness-next-person" @click="$emit('nextPerson')">
        {{ t('signing.witness.done.nextPerson') }}
      </PrimaryButton>
      <SecondaryButton v-if="followingNames?.length" type="button" data-testid="witness-next-person"
                       @click="$emit('nextPerson')">
        {{ t('signing.witness.done.nextPerson') }}
      </SecondaryButton>
      <SecondaryButton v-if="moreForPerson && !followingNames?.length" type="button" data-testid="witness-same-person"
                       @click="$emit('samePerson')">
        {{ t('signing.witness.done.samePerson', {name: personName}) }}
      </SecondaryButton>
    </ButtonRow>
  </SuccessContainer>
</template>
