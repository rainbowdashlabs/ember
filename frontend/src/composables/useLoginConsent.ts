/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { ref } from 'vue'
import { session } from '@/api'
import {
  OPTIONAL_NECESSITIES, acceptStorage, denyStorage, getConsent, getGrantedScopes,
  getStoredLegalVersions, type StorageConsent, type StorageNecessityName,
} from '@/api/storage'
import { useConsentGuard } from '@/composables/useConsentGuard'

/**
 * The storage-consent gate on the login page and the legal documents behind it.
 *
 * Consent is stored in the browser together with the document versions it was given for, so a
 * new version of any of the three documents withdraws the stored consent and the gate is shown
 * again. After a successful login the same versions are recorded against the account.
 */
export function useLoginConsent() {
  const consent = ref<StorageConsent | null>(getConsent())
  /** Starts with everything allowed, so declining a group is a deliberate act rather than the default. */
  const scopes = ref<StorageNecessityName[]>(
      consent.value === 'accepted' ? getGrantedScopes() : [...OPTIONAL_NECESSITIES])
  const consentHtml = ref('')
  const consentLoading = ref(false)

  const consentVersion = ref('')
  const privacyVersion = ref('')
  const tosVersion = ref('')

  const showPrivacyPolicy = ref(false)
  const privacyPolicyHtml = ref('')
  const privacyPolicyLoading = ref(false)

  const showTos = ref(false)
  const tosHtml = ref('')
  const tosLoading = ref(false)

  async function loadConsentText() {
    consentLoading.value = true
    try {
      const [consentData, versions] = await Promise.all([
        session.getConsentText(),
        session.getLegalVersions(),
      ])
      consentHtml.value = consentData.html
      consentVersion.value = versions.consentVersion
      privacyVersion.value = versions.privacyVersion
      tosVersion.value = versions.tosVersion

      const stored = getStoredLegalVersions()
      const outdated = stored.consent !== versions.consentVersion
        || stored.privacy !== versions.privacyVersion
        || stored.tos !== versions.tosVersion
      if (consent.value === 'accepted' && stored.consent && outdated) {
        consent.value = null
      }
    } catch {
      consentHtml.value = ''
    }
    consentLoading.value = false
  }

  /**
   * Opens a legal document, fetching it the first time it is asked for.
   */
  async function openDocument(
    shown: typeof showTos,
    html: typeof tosHtml,
    loading: typeof tosLoading,
    fetch: () => Promise<{html: string}>,
  ) {
    shown.value = true
    if (html.value) return
    loading.value = true
    try {
      html.value = (await fetch()).html
    } catch {
      html.value = ''
    }
    loading.value = false
  }

  function loadPrivacyPolicy() {
    return openDocument(showPrivacyPolicy, privacyPolicyHtml, privacyPolicyLoading,
      () => session.getPrivacyPolicy())
  }

  function loadTos() {
    return openDocument(showTos, tosHtml, tosLoading, () => session.getTermsOfService())
  }

  function acceptCurrentVersions() {
    acceptStorage({
      consent: consentVersion.value,
      privacy: privacyVersion.value,
      tos: tosVersion.value,
    }, scopes.value)
    consent.value = 'accepted'
  }

  function deny() {
    denyStorage()
    consent.value = 'denied'
  }

  /**
   * Records consent against the account after login. An account that consented to older versions
   * is flagged for re-consent rather than silently updated.
   */
  async function recordAfterLogin() {
    try {
      const status = await session.getConsentStatus()
      const current = {
        consent: status.currentConsentVersion,
        privacy: status.currentPrivacyVersion,
        tos: status.currentTosVersion,
      }
      if (!status.consented) {
        await session.recordConsent({
          consentVersion: consentVersion.value || status.currentConsentVersion,
          privacyVersion: privacyVersion.value || status.currentPrivacyVersion,
          tosVersion: tosVersion.value || status.currentTosVersion,
        })
        acceptStorage(current)
        return
      }
      if (!status.current) {
        useConsentGuard().setNeedsReconsent(true)
        return
      }
      acceptStorage(current)
    } catch {
      return
    }
  }

  return {
    consent,
    scopes,
    consentHtml,
    consentLoading,
    showPrivacyPolicy,
    privacyPolicyHtml,
    privacyPolicyLoading,
    showTos,
    tosHtml,
    tosLoading,
    loadConsentText,
    loadPrivacyPolicy,
    loadTos,
    acceptCurrentVersions,
    deny,
    recordAfterLogin,
  }
}
