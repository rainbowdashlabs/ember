/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { describeFailure, FailureKind, type Failure } from '@/util/failure'

/**
 * A public page reached by a link that carries its own credential in the query - a waiting-list
 * status link, a registration invite, and the like.
 *
 * There is no session behind these pages, so the credential is the whole authorisation and the
 * two ways it can fail need different wording: a link with nothing in it was probably truncated
 * in an email, while a link that the server rejects has expired or been used. The distinction is
 * the only thing the visitor can act on, so it is kept.
 *
 * <p>A third case was hidden inside the second: the server not rejecting the link but failing to
 * answer at all. Reporting that as an expired link sends somebody away from a link that still works,
 * and they have no account and nobody here to ask, so only an actual rejection says so now.
 *
 * @param queryKey       the query parameter carrying the credential
 * @param missingMessage shown when the link carries no credential at all
 * @param invalidMessage shown when the server rejects it
 * @param fetch          loads the resource the credential grants access to
 */
export function useLinkAccessedResource<T>(
  queryKey: string,
  missingMessage: () => string,
  invalidMessage: () => string,
  fetch: (credential: string) => Promise<T>,
) {
  const route = useRoute()
  const {t} = useI18n()

  const credential = ref('')
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(true)
  const failure = ref<Failure | null>(null)

  async function load() {
    credential.value = (route.query[queryKey] as string) ?? ''
    failure.value = null
    if (!credential.value) {
      failure.value = deadLink(missingMessage())
      loading.value = false
      return
    }
    try {
      data.value = await fetch(credential.value)
    } catch (e) {
      const described = describeFailure(e, t)
      failure.value = refused(described.kind) ? deadLink(invalidMessage()) : described
    } finally {
      loading.value = false
    }
  }

  /** Whether the server turned the link down, as opposed to failing to answer about it. */
  function refused(kind: Failure['kind']): boolean {
    return kind === FailureKind.GONE || kind === FailureKind.DENIED || kind === FailureKind.REJECTED
  }

  /** A link that will not work again, stated as a fact: there is nothing here to report. */
  function deadLink(message: string): Failure {
    return {
      kind: FailureKind.GONE,
      message,
      guidance: t('linkAccess.deadGuidance'),
      reportable: false,
    }
  }

  return {credential, data, loading, failure, load}
}
