// SPDX-License-Identifier: Apache-2.0
#ifndef MESHLIT_PLACEMENT_H
#define MESHLIT_PLACEMENT_H
#include <stdint.h>

/* Returns zero for an admitted plan. Only publishes outputs on success.
 * Caller supplies one element per worker in each array; manual may be null. */
int meshlit_place_layers(const int64_t *capacities, int workers, int64_t shared,
                         int blocks, int64_t overhead, const int32_t *manual,
                         int32_t *counts, int64_t *estimated);
#endif
