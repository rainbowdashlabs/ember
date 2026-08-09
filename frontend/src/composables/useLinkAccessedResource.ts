/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref, type Ref } from 'vue'
import { useRoute } from 'vue-router'

/**
 * A public page reached by a link that carries its own credential in the query — a waiting-list
 * status link, a registration invite, and the like.
 *
 * There is no session behind these pages, so the credential is the whole authorisation and the
 * two ways it can fail need different wording: a link with nothing in it was probably truncated
 * in an email, while a link that the server rejects has expired or been used. The distinction is
 * the only thing the visitor can act on, so it is kept.
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

  const credential = ref('')
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(true)
  const error = ref('')

  async function load() {
    credential.value = (route.query[queryKey] as string) ?? ''
    if (!credential.value) {
      error.value = missingMessage()
      loading.value = false
      return
    }
    try {
      data.value = await fetch(credential.value)
    } catch {
      error.value = invalidMessage()
    } finally {
      loading.value = false
    }
  }

  return {credential, data, loading, error, load}
}
