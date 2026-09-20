/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * What goes between two cells of a spreadsheet.
 *
 * <p>The reader decides, because a spreadsheet set up for German expects the semicolon and whatever
 * the file is fed to afterwards often insists on the comma. The server is told in these words and
 * answers with a file written that way.
 */
export type ExportSeparator = 'semicolon' | 'comma'

/** The formats a list can leave the product in. */
export type ExportFormat = 'csv' | 'pdf'
