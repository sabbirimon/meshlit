#include "hyperl.h"
#include <assert.h>
#include <float.h>
#include <math.h>
#include <stdio.h>
static int stop(void *ctx) {
    (void)ctx;
    return 1;
}
int main(void) {
    float x[] = {-1, 2, 3}, w[] = {2, 3, 4}, out[3] = {0};
    size_t n = 99;
    struct hl_vector inputs[] = {{x, 3}, {w, 3}};
    struct hl_step steps[] = {{HL_MULTIPLY, 0, 1}, {HL_RELU, 2, 0}, {HL_SUM, 3, 0}};
    assert(hl_execute(inputs, 2, steps, 2, 3, out, 3, &n, NULL, NULL) == HL_OK && n == 3 &&
           out[0] == 0 && out[1] == 6 && out[2] == 12 && x[0] == -1);
    assert(hl_execute(inputs, 2, steps, 3, 4, out, 3, &n, NULL, NULL) == HL_OK && n == 1 &&
           out[0] == 18);
    assert(hl_execute(inputs, 2, steps, 3, 4, out, 3, &n, stop, NULL) == HL_CANCELLED && n == 0);
    steps[0].a = 9;
    assert(hl_execute(inputs, 2, steps, 3, 4, out, 3, &n, NULL, NULL) == HL_INVALID && n == 0);
    steps[0].a = 0;
    x[0] = -FLT_MAX;
    w[0] = 2;
    assert(hl_execute(inputs, 2, steps, 2, 3, out, 3, &n, NULL, NULL) == HL_NONFINITE && n == 0);
    x[0] = NAN;
    assert(hl_execute(inputs, 2, steps, 2, 3, out, 3, &n, NULL, NULL) == HL_NONFINITE);
    {
        float overflow[] = {FLT_MAX, FLT_MAX, -FLT_MAX};
        struct hl_vector vector = {overflow, 3};
        struct hl_step sum = {HL_SUM, 0, 0};
        out[0] = 123;
        assert(hl_execute(&vector, 1, &sum, 1, 1, out, 3, &n, NULL, NULL) == HL_NONFINITE &&
               n == 0 && out[0] == 123);
    }
    inputs[0].length = 262145;
    assert(hl_execute(inputs, 2, steps, 2, 3, out, 3, &n, NULL, NULL) == HL_INVALID);
    puts("Portable C CPU checks passed: arithmetic, sum, immutability, cancel, DAG, bounds, "
         "nonfinite/overflow.");
    return 0;
}
