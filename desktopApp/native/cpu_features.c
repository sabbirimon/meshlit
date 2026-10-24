/* SPDX-License-Identifier: Apache-2.0 */
#include <cpuid.h>
#include <stdint.h>
#include <stdio.h>

int main(void) {
    unsigned a, b, c, d;
    if (!__get_cpuid(1, &a, &b, &c, &d) || !(c & (1u << 20))) {
        puts("unsupported");
        return 1;
    }
    const unsigned needed = (1u << 12) | (1u << 26) | (1u << 27) | (1u << 28) | (1u << 29);
    if ((c & needed) == needed) {
        unsigned low, high;
        /* XGETBV is safe only after CPU/OS XSAVE support is confirmed. */
        __asm__ volatile ("xgetbv" : "=a"(low), "=d"(high) : "c"(0));
        const uint64_t state = ((uint64_t)high << 32) | low;
        if ((state & 6u) == 6u && __get_cpuid_count(7, 0, &a, &b, &c, &d) && (b & (1u << 5))) {
            puts("avx2-fma-f16c");
            return 0;
        }
    }
    puts("baseline");
    return 0;
}
