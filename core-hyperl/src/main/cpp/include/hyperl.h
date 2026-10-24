#ifndef HYPERL_H
#define HYPERL_H
#include <stddef.h>
#include "hyperl_contract.h"
#ifdef __cplusplus
extern "C" {
#endif
enum hl_operation {
    HL_ADD = HL_OPCODE_ADD,
    HL_MULTIPLY = HL_OPCODE_MULTIPLY,
    HL_RELU = HL_OPCODE_RELU,
    HL_SUM = HL_OPCODE_SUM
};
enum hl_status { HL_OK = 0, HL_INVALID = 1, HL_MEMORY = 2, HL_NONFINITE = 3, HL_CANCELLED = 4 };
struct hl_vector {
    const float *data;
    size_t length;
};
/* Values 0..input_count-1 are inputs; each step creates the next immutable value. */
struct hl_step {
    enum hl_operation operation;
    size_t a;
    size_t b;
};
typedef int (*hl_cancel_fn)(void *context);
/* Bounded CPU reference only. Caller owns output and cancellation context.
   output_length is reset to zero on failure; incomplete output must be discarded.
   No OS, network, crypto, driver, pointers in language input or automatic plugins. */
enum hl_status hl_execute(const struct hl_vector *inputs, size_t input_count,
                          const struct hl_step *steps, size_t step_count, size_t output_index,
                          float *output, size_t output_capacity, size_t *output_length,
                          hl_cancel_fn cancelled, void *cancel_context);
const char *hl_version(void);
/* Optional precise-sum/1 primitive, separate from hyperl/1's ordered f32 sum.
   Uses compensated binary64 accumulation and one final finite f32 conversion.
   Caller keeps the input immutable for this call. Output is unchanged on error. */
enum hl_status hl_sum_precise(const struct hl_vector *input, float *output, hl_cancel_fn cancelled,
                              void *cancel_context);
#ifdef __cplusplus
}
#endif
#endif
