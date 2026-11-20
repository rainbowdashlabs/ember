/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {QueueOrder, type EventGroupDetails, type RegistrationDeadline} from '@/api/generated/schema'
import {deadlineRuleOf} from '../eventshared/registrationDeadline'

/**
 * The details of an event group as one field each: name, description, places, deadline, confirmation,
 * colour, minimum and the days it must be reached by, the places each part has and the order of the
 * waiting list.
 *
 * <p>The details come and go as one value in the shape the server keeps them, the deadline as a
 * rule counted from the start of the first part. The editor asks for them in different places of
 * its form, so each field reads and writes its own part of that value and leaves the others as they
 * are.
 *
 * @param details the details as one value
 */
export function useEventGroupDetails(details: Ref<EventGroupDetails>) {
  function update(change: Partial<EventGroupDetails>) {
    details.value = {...details.value, ...change}
  }

  return {
    name: computed({get: () => details.value.name ?? '', set: (value: string) => update({name: value})}),
    description: computed({
      get: () => details.value.description ?? '',
      set: (value: string) => update({description: value}),
    }),
    limit: computed({
      get: () => details.value.registrationLimit ?? undefined,
      set: (value: number | undefined) => update({registrationLimit: value ?? null}),
    }),
    deadline: computed({
      get: () => deadlineRuleOf(details.value.registrationDeadline),
      set: (value: RegistrationDeadline) => update({registrationDeadline: value}),
    }),
    confirm: computed({
      get: () => details.value.requiresConfirmation ?? false,
      set: (value: boolean) => update({requiresConfirmation: value}),
    }),
    color: computed({get: () => details.value.color ?? '', set: (value: string) => update({color: value || null})}),
    minRegistrations: computed({
      get: () => details.value.minRegistrations ?? undefined,
      set: (value: number | undefined) => update({minRegistrations: value ?? null}),
    }),
    thresholdDays: computed({
      get: () => details.value.thresholdDays ?? undefined,
      set: (value: number | undefined) => update({thresholdDays: value ?? null}),
    }),
    partLimit: computed({
      get: () => details.value.partRegistrationLimit ?? undefined,
      set: (value: number | undefined) => update({partRegistrationLimit: value ?? null}),
    }),
    queueOrder: computed({
      get: () => details.value.queueOrder ?? QueueOrder.TIME,
      set: (value: QueueOrder) => update({queueOrder: value}),
    }),
  }
}
