/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import {emptyRestriction, namesSomebody} from '@/components/input/restriction'
import type {RestrictionSelection} from '@/api/types'
import type {MemberGroup, UserTag} from '@/api/generated/schema'
import RestrictionRow from './RestrictionRow.vue'

/**
 * Who may sign up for something and who may see it, each added with its own button.
 *
 * <p>Both pickers used to stand open with a paragraph each, although most appointments are open to
 * everybody. Now a restriction shows once its button is pressed, or on its own as soon as it names
 * somebody, so something loaded or filled from a template shows what limits it. Once shown it stays
 * until it is taken away, also while its selection is emptied to pick another.
 */
const registration = defineModel<RestrictionSelection>('registration', {required: true})
const visibility = defineModel<RestrictionSelection>('visibility', {required: true})

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  /** How restricting the sign-up works, where the owner is not an appointment. */
  registrationHint?: string
  /** How restricting who sees it works, where the owner is not an appointment. */
  visibilityHint?: string
}>()

const {t} = useI18n()

const restrictsRegistration = ref(false)
const restrictsVisibility = ref(false)

watch(() => namesSomebody(registration.value), named => {
  if (named) restrictsRegistration.value = true
}, {immediate: true})
watch(() => namesSomebody(visibility.value), named => {
  if (named) restrictsVisibility.value = true
}, {immediate: true})

/** Takes a restriction away again, which opens it to everybody on that count. */
function liftRestriction(which: 'registration' | 'visibility') {
  if (which === 'registration') {
    restrictsRegistration.value = false
    registration.value = emptyRestriction()
  } else {
    restrictsVisibility.value = false
    visibility.value = emptyRestriction()
  }
}
</script>

<template>
  <RestrictionRow
      v-if="restrictsRegistration"
      v-model="registration"
      :label="t('events.restrictedRegistration')"
      :hint="registrationHint ?? t('events.restrictToRolesHint')"
      :groups="groups"
      :tags="tags"
      data-testid="restriction-registration"
      @remove="liftRestriction('registration')"
  />
  <RestrictionRow
      v-if="restrictsVisibility"
      v-model="visibility"
      :label="t('events.restrictedVisibility')"
      :hint="visibilityHint ?? t('events.restrictVisibilityHint')"
      :groups="groups"
      :tags="tags"
      data-testid="restriction-visibility"
      @remove="liftRestriction('visibility')"
  />
  <ButtonRow v-if="!restrictsRegistration || !restrictsVisibility">
    <SecondaryButton v-if="!restrictsRegistration" :icon="['fas', 'plus']" data-testid="restrict-registration"
                     @click="restrictsRegistration = true">
      {{ t('events.restrictToRoles') }}
    </SecondaryButton>
    <SecondaryButton v-if="!restrictsVisibility" :icon="['fas', 'plus']" data-testid="restrict-visibility"
                     @click="restrictsVisibility = true">
      {{ t('events.restrictVisibility') }}
    </SecondaryButton>
  </ButtonRow>
</template>
