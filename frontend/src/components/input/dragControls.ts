/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The width the sorting controls of a {@code DragList} take beside a row of a single line.
 *
 * <p>Fixed rather than measured, and the same whether the grip is shown or not, so that a heading
 * written above such a list lines up with the rows under it by leaving exactly this much room. A row
 * tall enough to stand its controls on top of each other needs less than this and takes less, which
 * is why only a list of short rows, such as a table with a header, can rely on it.
 */
export const DRAG_CONTROL_COLUMN = 'w-20'
