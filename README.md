Filter [![Build Status](https://travis-ci.org/ucb-art/filter.svg?branch=master)](https://travis-ci.org/ucb-art/filter)
=======================

# Overview

This project contains a decimating FIR filter.

# Usage

## GitHub Pages

See [here](https://ucb-art.github.io/filter/latest/api/) for the GitHub pages scaladoc.

## Setup

Clone the repository and update the depenedencies:

```
git clone git@github.com:ucb-art/filter.git
git submodule update --init
cd dsp-framework
./update.bash no_hwacha
cd ..
```

See the [https://github.com/ucb-art/dsp-framework/blob/master/README.md](dsp-framework README) for more details on this infrastructure.
Build the dependencies by typing `make libs`.

## Building

The build flow generates FIRRTL, then generates Verilog, then runs the TSMC memory compiler to generate memories.
Memories are black boxes in the Verilog by default.
IP-Xact is created with the FIRRTL.
The build targets for each of these are firrtl, verilog, and mems, respectively.
Depedencies are handled automatically, so to build the Verilog and memories, just type `make mems`.
Results are placed in a `generated-src` directory.

## Testing

Chisel testing hasn't been implemented yet.

## Configuring

In `src/main/scala` there is a `Config.scala` file.
A few default configurations are defined for you, called DefaultStandaloneXTunerConfig, where X is either Real, FixedPoint, or Complex.
The inputs, outputs, and coefficients must have the same underlying type currently.
These generate a small filter with default parameters.
To run them, type `make verilog CONFIG=DefaultStandaloneXFilterConfig`, replacing X with Real, FixedPoint, or Complex.
The default make target is the default FixedPoint configuration.

The suggested way to create a custom configuration is to modify CustomStandaloneFilterConfig, which defines values for all possible parameters.
Then run `make verilog CONFIG=CustomStandaloneFilterConfig` to generate the Verilog.

# Specifications

## Interfaces

The Filter uses the [https://github.com/ucb-art/rocket-dsp-utils/blob/master/doc/stream.md](DSP streaming interface) (a subset of AXI4-Stream) on both the data input and data output.
The SCRFile contains the filter coefficients, as well as data set end flag registers.
It is accessible through an AXI4 interface.

## Signaling

### Bits

It is expected that the bits inputs contain time-series data time-multiplexed on the inputs, such that on the first cycle are values x[0], x[1], …, x[p-1], then the next cycle contains x[p], x[p+1], … and this continues indefinitely. 
The outputs are in same time order, but are decimated if the number of outputs is less than the number of inputs.
The filter accepts real or complex values.

### Valid

The output valid is just the input valid delayed by the processing delay parameter.

### Sync

The output sync is just the input sync delayed by the processing delay parameter.

## Implementation

The filter implements an FIR filter, with decimation done by dividing down the number of parallel outputs.
The user can choose any number of taps and parallel inputs.
The number of outputs must be an integer divisor of the number of inputs.
All outputs are calculated, and it is assumed that synthesis tools will remove unnecessary logic if there are fewer outputs than inputs.
