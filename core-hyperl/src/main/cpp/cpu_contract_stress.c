#include "hyperl.h"
#include <assert.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>

static uint32_t state = 20261008;
static uint32_t next_random(void) {
    state = state * 1664525u + 1013904223u;
    return state;
}
static int stop(void *context) {
    (void)context;
    return 1;
}

int main(void) {
    float x[1025], w[1025], output[1025];
    size_t coverage[16] = {0};
    for (size_t iteration = 0; iteration < 4096; iteration++) {
        size_t length = 1 + (next_random() >> 16) % 1025;
        float expected = 0;
        for (size_t i = 0; i < length; i++) {
            x[i] = (float)((int)((next_random() >> 16) % 17) - 8) * 0.25f;
            w[i] = (float)((int)((next_random() >> 16) % 9) - 4) * 0.5f;
            float product = x[i] * w[i];
            expected += product > 0 ? product : 0;
        }
        struct hl_vector inputs[HL_MAX_INPUTS] = {{x, length}, {w, length}};
        struct hl_step steps[] = {{HL_MULTIPLY, 0, 1}, {HL_RELU, 2, 0}, {HL_SUM, 3, 0}};
        size_t input_count = 2, step_count = 3, output_index = 4, capacity = 1025;
        hl_cancel_fn cancelled = NULL;
        enum hl_status expected_status = HL_INVALID;
        size_t category = (next_random() >> 16) % 16;
        coverage[category]++;
        switch (category) {
        case 0:
            expected_status = HL_OK;
            break;
        case 1:
            steps[0].a = 2;
            break;
        case 2:
            inputs[1].length = length - 1;
            break;
        case 3:
            step_count = 0;
            break;
        case 4:
            input_count = 0;
            break;
        case 5:
            input_count = HL_MAX_INPUTS + 1;
            break;
        case 6:
            step_count = HL_MAX_STEPS + 1;
            break;
        case 7:
            steps[0].operation = (enum hl_operation)99;
            break;
        case 8:
            inputs[0].length = HL_MAX_VECTOR_ELEMENTS + 1;
            break;
        case 9:
            capacity = 0;
            break;
        case 10:
            inputs[0].data = NULL;
            break;
        case 11:
            x[0] = NAN;
            expected_status = HL_NONFINITE;
            break;
        case 12:
            cancelled = stop;
            expected_status = HL_CANCELLED;
            break;
        case 13:
            steps[1].a = 4;
            break;
        case 14:
            output_index = 5;
            break;
        default:
            // Rejected graph admission must happen before reading oversized
            // declared buffers. ASan catches accidental early input access.
            input_count = 4;
            for (size_t i = 0; i < input_count; i++) {
                inputs[i].data = x;
                inputs[i].length = HL_MAX_VECTOR_ELEMENTS;
            }
            steps[0] = (struct hl_step){HL_RELU, 0, 0};
            expected_status = HL_MEMORY;
            break;
        }
        size_t result_length = 777;
        output[0] = 12345;
        enum hl_status status = hl_execute(inputs, input_count, steps, step_count, output_index,
                                           output, capacity, &result_length, cancelled, NULL);
        assert(status == expected_status);
        if (status == HL_OK) {
            assert(result_length == 1 && output[0] == expected);
        } else {
            assert(result_length == 0 && output[0] == 12345);
        }
    }
    for (size_t category = 0; category < 16; category++) {
        assert(coverage[category] > 0);
    }
    float cancellation[] = {16777216, 1, -16777216};
    struct hl_vector input = {cancellation, 3};
    float precise = 12345;
    assert(hl_sum_precise(&input, &precise, NULL, NULL) == HL_OK && precise == 1);
    assert(hl_sum_precise(&input, &precise, stop, NULL) == HL_CANCELLED && precise == 1);
    input.length = HL_MAX_VECTOR_ELEMENTS + 1;
    assert(hl_sum_precise(&input, &precise, NULL, NULL) == HL_INVALID && precise == 1);
    puts("4096 seeded C contract cases passed; precise sum and no partial publication checked.");
    return 0;
}
