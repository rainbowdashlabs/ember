/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import RowLink from '@/components/navigation/RowLink.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {FormPurpose, FormStatus, FormVisibility, type Form} from '@/api/forms'
import {PublicFormState} from '@/api/publicForms'
import {closedSinceOf, formStateOf} from '@/util/formState'
import {formatDate, formatDateTime} from '@/util/format'
import FormTileMenu from './FormTileMenu.vue'

const props = defineProps<{
  form: Form
  canCreatePolls: boolean
  /** Names whether a form is taking answers, which is not the same as its stored status. */
  statusLabel: (state: string) => string
  /** The page the tile opens, or nothing where it opens none. */
  to: RouteLocationRaw | null
}>()

const emit = defineEmits<{
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'share', form: Form): void
  (e: 'clear', form: Form): void
  (e: 'delete', form: Form): void
}>()

const {t} = useI18n()

/** Whether the form is taking answers, which its dates decide as much as its status does. */
const state = computed(() => formStateOf(props.form))

/** When it stopped, for a form that has: a closed survey is usually one somebody has to date. */
const closedSince = computed(() => closedSinceOf(props.form))

/**
 * A form answered from outside that has been set to answer at its link and nowhere else. Worth
 * saying on the tile, because it is the difference between a form anybody can find and one only the
 * people who were sent it can.
 */
const sentByLinkAlone = computed(
    () => props.form.purpose !== FormPurpose.INTERNAL && props.form.visibility === FormVisibility.UNLISTED)
</script>

<template>
  <RowLink :to="props.to">
    <NeutralContainer class="relative cursor-pointer hover:border-primary transition-colors h-full">
      <div class="flex flex-col gap-2 pr-10 h-full">
        <div class="flex items-center gap-2 flex-wrap">
          <SuccessBadge v-if="state === PublicFormState.OPEN">{{ statusLabel(state) }}</SuccessBadge>
          <ErrorBadge v-else-if="state === PublicFormState.CLOSED">{{ statusLabel(state) }}</ErrorBadge>
          <InfoBadge v-else>{{ statusLabel(state) }}</InfoBadge>
          <SecondaryBadge v-if="form.status !== FormStatus.DRAFT">
            {{ t('forms.responseCount', {count: form.responseCount}) }}
          </SecondaryBadge>
          <InfoBadge v-if="sentByLinkAlone">{{ t('forms.unlistedBadge') }}</InfoBadge>
          <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']"/>
        </div>
        <div class="font-semibold">{{ form.title }}</div>
        <p v-if="form.description" class="text-xs text-(--text-muted) line-clamp-3">{{ form.description }}</p>
        <div class="mt-auto pt-2 text-xs text-(--text-muted) flex flex-col gap-0.5">
          <span>{{ t('forms.createdOn', {when: formatDate(form.createdAt)}) }}</span>
          <span>{{ t('forms.lastActivityOn', {when: formatDate(form.lastActivityAt ?? form.updatedAt)}) }}</span>
          <span v-if="closedSince">{{ t('forms.closedOn', {when: formatDateTime(closedSince)}) }}</span>
        </div>
      </div>

      <FormTileMenu
        :form="form"
        :can-create-polls="canCreatePolls"
        @publish="emit('publish', $event)"
        @close="emit('close', $event)"
        @edit="emit('edit', $event)"
        @analytics="emit('analytics', $event)"
        @share="emit('share', $event)"
        @clear="emit('clear', $event)"
        @delete="emit('delete', $event)"
      />
    </NeutralContainer>
  </RowLink>
</template>
