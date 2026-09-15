/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The shortest password the backend accepts, mirroring `PasswordPolicy.MIN_LENGTH`. Kept here so a
 * form can say the requirement before the request goes out; the server remains the one that
 * enforces it.
 */
export const PASSWORD_MIN_LENGTH = 12
