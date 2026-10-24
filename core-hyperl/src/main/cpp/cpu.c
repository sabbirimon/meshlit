#include "hyperl.h"

#include <float.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#if FLT_RADIX != 2 || FLT_MANT_DIG != 24 || FLT_MAX_EXP != 128
#error "HyperL CPU ABI requires IEEE binary32 float semantics"
#endif
typedef char hl_float_must_be_4_bytes[sizeof(float) == 4 ? 1 : -1];

const char *hl_version(void) { return HL_RUNTIME_REVISION; }

enum hl_status hl_execute(const struct hl_vector *inputs, size_t input_count,
                          const struct hl_step *steps, size_t step_count, size_t output_index,
                          float *output, size_t output_capacity, size_t *output_length,
                          hl_cancel_fn cancelled, void *cancel_context) {
    size_t lengths[HL_MAX_VALUES] = {0};
    float *values[HL_MAX_VALUES] = {0};
    size_t retained = 0;

    if (!output_length) {
        return HL_INVALID;
    }
    *output_length = 0;
    if (!inputs || !steps || !output || input_count < 1 || input_count > HL_MAX_INPUTS ||
        step_count < 1 || step_count > HL_MAX_STEPS || output_index >= input_count + step_count) {
        return HL_INVALID;
    }

    /* Validate the whole graph before allocating or reading any input values. */
    for (size_t i = 0; i < input_count; i++) {
        if (!inputs[i].data || inputs[i].length < 1 || inputs[i].length > HL_MAX_VECTOR_ELEMENTS) {
            return HL_INVALID;
        }
        lengths[i] = inputs[i].length;
        retained += lengths[i];
        if (retained > HL_MAX_RETAINED_ELEMENTS) {
            return HL_MEMORY;
        }
    }
    for (size_t i = 0; i < step_count; i++) {
        const struct hl_step step = steps[i];
        size_t index = input_count + i;
        if (step.a >= index || step.operation < HL_ADD || step.operation > HL_SUM) {
            return HL_INVALID;
        }
        if ((step.operation == HL_ADD || step.operation == HL_MULTIPLY) &&
            (step.b >= index || lengths[step.a] != lengths[step.b])) {
            return HL_INVALID;
        }
        lengths[index] = step.operation == HL_SUM ? 1 : lengths[step.a];
        retained += lengths[index];
        if (retained > HL_MAX_RETAINED_ELEMENTS) {
            return HL_MEMORY;
        }
    }
    if (output_capacity < lengths[output_index]) {
        return HL_INVALID;
    }

    enum hl_status status = HL_OK;
    for (size_t index = 0; index < input_count + step_count; index++) {
        if (cancelled && cancelled(cancel_context)) {
            status = HL_CANCELLED;
            goto done;
        }
        values[index] = malloc(lengths[index] * sizeof(float));
        if (!values[index]) {
            status = HL_MEMORY;
            goto done;
        }
        if (index < input_count) {
            for (size_t i = 0; i < lengths[index]; i++) {
                if (i % HL_CANCELLATION_INTERVAL == 0 && cancelled && cancelled(cancel_context)) {
                    status = HL_CANCELLED;
                    goto done;
                }
                float value = inputs[index].data[i];
                if (!isfinite(value)) {
                    status = HL_NONFINITE;
                    goto done;
                }
                values[index][i] = value;
            }
            continue;
        }

        const struct hl_step step = steps[index - input_count];
        const float *a = values[step.a];
        if (step.operation == HL_SUM) {
            float sum = 0;
            for (size_t i = 0; i < lengths[step.a]; i++) {
                if (i % HL_CANCELLATION_INTERVAL == 0 && cancelled && cancelled(cancel_context)) {
                    status = HL_CANCELLED;
                    goto done;
                }
                /* Version 1 deliberately preserves ordered binary32 addition. */
                sum += a[i];
                if (!isfinite(sum)) {
                    status = HL_NONFINITE;
                    goto done;
                }
            }
            values[index][0] = sum;
        } else {
            for (size_t i = 0; i < lengths[index]; i++) {
                if (i % HL_CANCELLATION_INTERVAL == 0 && cancelled && cancelled(cancel_context)) {
                    status = HL_CANCELLED;
                    goto done;
                }
                float value;
                switch (step.operation) {
                case HL_ADD:
                    value = a[i] + values[step.b][i];
                    break;
                case HL_MULTIPLY:
                    value = a[i] * values[step.b][i];
                    break;
                default: /* Validation has limited this branch to ReLU. */
                    value = a[i] > 0 ? a[i] : 0;
                    break;
                }
                if (!isfinite(value)) {
                    status = HL_NONFINITE;
                    goto done;
                }
                values[index][i] = value;
            }
        }
    }

    /* Publish only after all intermediate values passed validation. */
    memcpy(output, values[output_index], lengths[output_index] * sizeof(float));
    *output_length = lengths[output_index];
done:
    for (size_t i = 0; i < HL_MAX_VALUES; i++) {
        free(values[i]);
    }
    return status;
}

enum hl_status hl_sum_precise(const struct hl_vector *input, float *output, hl_cancel_fn cancelled,
                              void *cancel_context) {
    if (!input || !input->data || !output || input->length < 1 ||
        input->length > HL_MAX_VECTOR_ELEMENTS || DBL_MANT_DIG < 53) {
        return HL_INVALID;
    }
    double sum = 0;
    double correction = 0;
    for (size_t i = 0; i < input->length; i++) {
        if (i % HL_CANCELLATION_INTERVAL == 0 && cancelled && cancelled(cancel_context)) {
            return HL_CANCELLED;
        }
        double value = input->data[i];
        if (!isfinite(value)) {
            return HL_NONFINITE;
        }
        double next = sum + value;
        if (fabs(sum) >= fabs(value)) {
            correction += (sum - next) + value;
        } else {
            correction += (value - next) + sum;
        }
        sum = next;
    }
    double total = sum + correction;
    if (!isfinite(total) || total > FLT_MAX || total < -FLT_MAX) {
        return HL_NONFINITE;
    }
    float result = (float)total;
    if (!isfinite(result)) {
        return HL_NONFINITE;
    }
    if (cancelled && cancelled(cancel_context)) {
        return HL_CANCELLED;
    }
    *output = result;
    return HL_OK;
}
