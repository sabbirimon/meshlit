// SPDX-License-Identifier: Apache-2.0
#include "placement.h"

int meshlit_place_layers(const int64_t *capacities, int workers, int64_t shared,
                         int blocks, int64_t overhead, const int32_t *manual,
                         int32_t *counts, int64_t *estimated) {
    if (!capacities || !counts || !estimated || blocks < 2 || blocks > 1024 ||
        workers < 2 || workers > 64 || workers > blocks || shared <= 0 ||
        shared > INT64_MAX / 4 || overhead < 0 || overhead > INT64_MAX / 4) return 1;
    const int64_t layer = shared / blocks + (shared % blocks != 0);
    const int64_t reserve = overhead + layer;
    int32_t limit[64], assigned[64];
    int total = 0, capacity = 0;
    for (int i = 0; i < workers; ++i) {
        if (capacities[i] <= reserve || capacities[i] > (INT64_C(1) << 50)) return 1;
        const int64_t available = (capacities[i] - reserve) / layer;
        limit[i] = available < blocks ? (int32_t)available : blocks;
        assigned[i] = manual ? manual[i] : 1;
        if (assigned[i] < 1 || assigned[i] > limit[i]) return 1;
        total += assigned[i];
        capacity += limit[i];
    }
    if (capacity < blocks || (manual && total != blocks)) return 1;
    while (total < blocks) {
        int best = -1;
        for (int i = 0; i < workers; ++i) {
            if (assigned[i] >= limit[i]) continue;
            if (best < 0 || (assigned[i]+1)*limit[best] < (assigned[best]+1)*limit[i]) best = i;
        }
        if (best < 0) return 1;
        ++assigned[best];
        ++total;
    }
    for (int i = 0; i < workers; ++i) {
        counts[i] = assigned[i];
        estimated[i] = reserve + layer * assigned[i];
    }
    return 0;
}
