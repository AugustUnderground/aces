# REST API for Analog Circuit Library

REST API for
[analog-circuit-library](https://gitlab-forschung.reutlingen-university.de/schweikm/analog-circuit-library).
Simulate and characterize Operational Amplifiers from anywhere in the ~~world~~
universe.

## Installation

### Dependencies

- [analog-circuit-library](https://gitlab-forschung.reutlingen-university.de/schweikm/analog-circuit-library)
- clojure >= 1.10.3

**Optional**: [curl](https://curl.se/) and [jq](https://stedolan.github.io/jq/)
to send and receive from the command line.

### Setup

To generate `pom.xml`:

```bash
$ lein install
```

## Getting Started

```bash
$ lein trampoline run --tech <tech> --ckt <ckt>
```

**NOTE**: `trampoline` is required for graceful exit. (see 
[this answer](https://stackoverflow.com/a/10863953))

If all went well it should say:

```bash
Server started on Port XXXX
```

Then, in another terminal:

```bash
$ curl -X GET localhost:XXXX/rng/op1
```

The POST request to `sim/op#` has to be of type `application/json`. If it's
empty, the current state of the circuit will be simulated. These results are
**not** cached on the server. Each request will be simulated again.

## Usage

The CLI supports the following arguments:

```bash
$ lein run --tech <TECH> --ckt <CKT> [--sim <SIM>] [--op <OP>] [--port <PORT>]
    where:
        --tech <TECH>       Path to PDK/Technology
        --ckt <CKT>         Path to op netlist and testbench
        --sim <SIM>         Path to simultation directory, defaults to /tmp
        --op <OP>           OpAmp ID: 1 - Miller Amplifier
                                      2 - Symmetrical Amplifier
        --port <PORT>       Port, defaults to 8888
```

**NOTE:** At this point only list values are supported. So even if just one
value is to be simulated, it should be in a singleton list.

The following will simulate the circuit for the three given sizing combinations
and returns a JSON header with the corresponding simulation results.

## Routes

Three routes are supported as of now.

### Available Sizing Parameters

To get a list of available parameters send a `GET` request to `params/op#`:

```bash
$ curl -X GET localhost:XXXX/params/op2 | jq
```

### Random Sizing

To get random and legal sizing parameters send a `GET` request to `rng/op#`:

```bash
$ curl -X GET localhost:XXXX/rng/op1 | jq
```

### Simulation

To simulate the current netlist send an **empty** `POST` request to `sim/op#`:

```bash
$ curl -X POST localhost:XXXX/sim/op2 \
    -H 'Content-Type: application/json' -d '{}' | jq
```

To simulate a different set of parameters send them as JSON:

```bash
$ curl -X POST localhost:8888/sim/op1 -H 'Content-Type: application/json' \
       -d '{"Wd": [2e-6, 3e-6, 4e-6], "Ld": [2e-6, 3e-6, 4e-6]}' | jq
```

The lists of values for each parameter should be equal, for this example 3
simulations would be run with the corresponding sizing and return a JSON object
where each performance parameter has a list of equal length (3 in this case).

## License

Copyright (c) 2021 Yannick Uhlmann

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice (including the next
paragraph) shall be included in all copies or substantial portions of the
Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
