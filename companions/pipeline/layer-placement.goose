// SPDX-License-Identifier: Apache-2.0
// Host-side equivalent of ClusterNegotiation's automatic LayerPlacement step.
import os;
import std;

fn number(s: u8[:], low: i64, high: i64) -> i64 {
    let n, ok = parse_int(s);
    if !ok || n < low || n > high { abort("out-of-range planner input"); }
    return n;
}

fn allocate(limits: i64[:], blocks: i64) -> i64[>..] {
    var counts: i64[>..] = [];
    for i in limits.len { counts.push(1); }
    for layer in limits.len..blocks {
        var best = -1;
        for i in limits.len {
            if counts[i] < limits[i] {
                if best < 0 || (counts[i] + 1) * limits[best] < (counts[best] + 1) * limits[i] { best = i; }
            }
        }
        if best < 0 { abort("insufficient worker capacity"); }
        counts[best]++;
    }
    return counts;
}

fn main() {
    let argv = args();
    if argv.len < 6 || argv.len > 68 { abort("expected blocks shared_bytes overhead_bytes capacity1 capacity2 ..."); }
    let blocks = number(argv[1], 2, 1024);
    if argv.len - 4 > blocks { abort("more workers than blocks"); }
    let shared = number(argv[2], 1, 2305843009213693951);
    let overhead = number(argv[3], 0, 2305843009213693951);
    let per_layer = 1 + (shared - 1) / blocks;
    var limits: i64[>..] = [];
    for i in 4..argv.len {
        let budget = number(argv[i], 1, 1125899906842624);
        let limit = (budget - overhead - per_layer) / per_layer;
        if limit < 1 { abort("worker cannot admit one layer"); }
        limits.push(min(limit, blocks));
    }
    let counts = allocate(limits, blocks);
    for count in counts { print(count); }
}
