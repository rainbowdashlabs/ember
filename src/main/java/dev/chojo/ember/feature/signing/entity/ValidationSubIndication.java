/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/**
 * Why a validation did not pass, with the names ETSI EN 319 102-1 gives the reasons, and one of Ember's
 * own at the end.
 */
public enum ValidationSubIndication {
    /** The file or the signature is not in a form the validator can read. */
    FORMAT_FAILURE,
    /** The signed content changed after it was signed. */
    HASH_FAILURE,
    /** The signature value does not match the signer's key. */
    SIG_CRYPTO_FAILURE,
    /** The signer's certificate was revoked before the signature can be proven to exist. */
    REVOKED,
    /** The signer's certificate had expired. */
    EXPIRED,
    /** The signer's certificate was not valid yet. */
    NOT_YET_VALID,
    /** The signature does not meet the validation policy's constraints. */
    SIG_CONSTRAINTS_FAILURE,
    /** The certificate chain does not meet the validation policy's constraints. */
    CHAIN_CONSTRAINTS_FAILURE,
    /** The certificate chain could not be checked. */
    CERTIFICATE_CHAIN_GENERAL_FAILURE,
    /** An algorithm or key size is too weak for the validation policy. */
    CRYPTO_CONSTRAINTS_FAILURE,
    /** The validation policy could not be processed. */
    POLICY_PROCESSING_ERROR,
    /** The signature policy the signature names is not available. */
    SIGNATURE_POLICY_NOT_AVAILABLE,
    /** The timestamps are in an order that contradicts each other. */
    TIMESTAMP_ORDER_FAILURE,
    /** The signer's certificate is not in the file. */
    NO_SIGNING_CERTIFICATE_FOUND,
    /** The chain does not end in a certificate the validation trusts. */
    NO_CERTIFICATE_CHAIN_FOUND,
    /** The chain does not end in a trusted certificate, and nothing proves when it did. */
    NO_CERTIFICATE_CHAIN_FOUND_NO_POE,
    /** The signer's certificate is revoked, and nothing proves the signature existed before. */
    REVOKED_NO_POE,
    /** An issuing certificate is revoked, and nothing proves the signature existed before. */
    REVOKED_CA_NO_POE,
    /** The signing time lies outside the certificate's validity, which was not revoked. */
    OUT_OF_BOUNDS_NOT_REVOKED,
    /** The signing time lies outside the certificate's validity, and nothing proves otherwise. */
    OUT_OF_BOUNDS_NO_POE,
    /** The revocation data lies outside the certificate's validity, and nothing proves otherwise. */
    REVOCATION_OUT_OF_BOUNDS_NO_POE,
    /** An algorithm became too weak, and nothing proves the signature existed before. */
    CRYPTO_CONSTRAINTS_FAILURE_NO_POE,
    /** Nothing proves when the signature existed. */
    NO_POE,
    /** Revocation data that would decide is not available yet. */
    TRY_LATER,
    /** The signed data could not be found. */
    SIGNED_DATA_NOT_FOUND,
    /** An attestation does not meet the validation policy's constraints. */
    ATTESTATION_CONSTRAINTS_FAILURE,
    /**
     * Not a standard reason but Ember's own: the signature's chain does not end in one of this
     * installation's authorities, so no station here sealed it, whatever the validator made of it.
     */
    NOT_ISSUED_HERE
}
