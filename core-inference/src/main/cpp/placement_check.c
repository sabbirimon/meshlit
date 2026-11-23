// SPDX-License-Identifier: Apache-2.0
#include "placement.h"
#include <stdint.h>
#include <stdio.h>

int main(void) {
    const int64_t cap[] = {1000000000,650000000};
    const int32_t manual[] = {12,12}, bad[] = {24,0};
    int32_t counts[] = {-1,-1};
    int64_t bytes[] = {-1,-1};
    if (meshlit_place_layers(cap,2,508177248,24,268435456,NULL,counts,bytes) ||
        counts[0]!=14 || counts[1]!=10 || bytes[0]>cap[0] || bytes[1]>cap[1]) return 1;
    if (meshlit_place_layers(cap,2,508177248,24,268435456,manual,counts,bytes) ||
        counts[0]!=12 || counts[1]!=12) return 2;
    if (!meshlit_place_layers(cap,2,508177248,24,268435456,bad,counts,bytes) ||
        counts[0]!=12 || counts[1]!=12) return 3;
    const int64_t equal[] = {1000,1000};
    if (meshlit_place_layers(equal,2,100,3,0,NULL,counts,bytes) ||
        counts[0]!=2 || counts[1]!=1 || bytes[0]!=102 || bytes[1]!=68) return 4;
    puts("native layer placement: automatic, manual, rejection and ties passed");
    return 0;
}
