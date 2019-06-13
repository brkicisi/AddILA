# AddILA

Add an ILA to a Vivado project using RapidWright.

Vivado lets you add an ila to a placed design, but routing the design with the ila may take longer and/or change how your design is routed. Errors may change due to the rerouting which cannot be debugged easily.

The advantage of using ILADebug is that it can add an ila and probes to a routed design.

Some warnings and fixes I used are recorded in [notes](#notes)

## ILADebug

ILADebug.java is the main worker. It calls upon RapidWright libraries to add an ila and probes to an already placed and routed design.

### Running ILADebug

ILADebug (hereafter the program) can be run like any other java file. It requires the RapidWright
directory in the class path as well as $CLASSPATH (which is set or appended to by rapidwright.tcl). From the directory containing ILADebug.java:

compile: `javac ILADebug.java`\
run: `java -cp .:<RapidWright_dir>/RapidWright:$CLASSPATH ILADebug <args ...>`

#### Arguments

##### Input dcp

- The program expects that the input dcp was generated using Vivado at `<project_name>.runs/impl_1/<design_name>_wrapper_routed.dcp`.
- The parts of this that it depends on are
  - The input dcp has been placed. The input dcp being routed shouldn't be necessary, but at that point why aren't you just adding an ila in Vivado?
  - The filename having `*_wrapper_*` is used when generating default file names.
    - This may cause errors if the program can't find, or finds the wrong, file.

##### .iii Directory

This program keeps intermediate solutions and files in a directory named `.iii`.

The `-d` or `--iii_dir` flag can be used with a path as an argument to specify an existing `.iii`
file or the location in which to create the directory.

For other file inputs to the command line you can use the shortcut `#iii/<filename>` to mean `<iii_dir>/<filename>`.

##### Probe Count

The idea of this argument is that you can reserve space to expand the number of probes without needing to reinsert an ila with more probe connections later.

##### Help

Further help with the arguments can be found by invoking run with any set of arguments including `-h` or `--help`.

### Specifying Probes

There are 2 ways to choose which nets in your design are connected to the probes.

1. Mark nets for debug.
   - This is the default option. The program will automatically call a rapidwright function which will search the design checkpoint it opens for nets that have the property `mark_debug` (in edif) or `MARK_DEBUG` (in xdc).
   - The nets will be connected to probes numbered 0 to n with no guarentees for ordering (though the list of connections will be written to a probes file with the same directory and name as the output dcp).
2. Specify a probes file.
   - There is a flag in the command line arguments that lets you specify a [probes file](#probes-file).

## Probes File

- A list of key value pairs of probes to nets in the original design. Each line should be of the form:
  > `top/u_ila_0/probe0[<index>] <net/in/original/design>`
  - ie. `<probe><split=" "><net>`
- If a probe is not written as `top/u_ila_0/probe0[<index>]` then the program may reassign the index as whatever it chooses.
- Lines beginning with a hash character (`#`) are ignored as commments.
- Empty lines cause errors.
- Ordering of the probe indecies does not matter in the probe file. However, the number of probe wires requested is determined by the largest index in the probe file.
- To ensure the final set of probes is contiguous and covers all probe wires, the program will try to connect any unconnected probe wires (indecies which have not been specified) to a reset net if it can find one, or some other net if it can't.
- Probe indicies must be between 0 and 4095 (inclusive).
- The output probes file will show how the program actually connected the probes and nets together.

## Old

This contains mostly some early work that was combined together to form the basis of ILADebug. 

The files here contain pieces of the process to add an ila and probes.

## Notes

1. Modified `rapidwright.tcl:571`
   - This should only be necessary once. It removes RapidWright trying to call LSF to manage queing and runs.
   > `- launch_runs $synth_run -lsf {bsub -R "select[osdistro=rhel && (osver=ws6)]" -N -q medium}`\
   > `+ launch_runs $synth_run`
1. The process `create_preimplemented_ila_dcp` in `rapidwright.tcl` uses `design_1` as a fixed constant. Do not name your block diagram the same or you will get an error.
1. After RapidWright adds an ila using `ILAInserter`, the resulting checkpoint cannot be opened in Vivado.
   - To fix this, the following experimentally determined solution is used.
   - In the edif corresponding to the checkpoint, the following replacement is made.
   > `- (instance top (viewref design_1_wrapper (cellref design_1_wrapper (libraryref work)))`\
   > `+ (instance top (viewref netlist (cellref design_1_wrapper (libraryref work)))`
1. The following functions from RapidWright code gave errors. To solve this, modified versions are used in ILADebug.java (renamed `my_<original_function_name>`).
   1. `EDIFTools.connectDebugProbe()`
   1. `ProbeRouter.updateProbeConnections()`

## Bugs

I am sure there must be still bugs.

There has only been limited testing done so far. All of it using the same initial dcp file.

## History

### Version 1

#### v1.1

- 12 June 2019
- Allows for changing number of probes between runs.
- Handles changing the clk_net or probe_depth between runs (adds .iii/metadata.txt).
- Added shortcut so `#iii/` replaces .iii dir for files on command line.
- Sometimes the program cannot route probes even if it previously succeeded with the probes in a different order.
- At least one example of adding more probes allowing routing to complete successfully and bitgen to complete successfully.
- These errors may be due to randomized nature of some of place and route.

#### v1.0

- 7 June 2019
- Works successfully on a simple test project (Vivado tutorial 2) on Nexys 4 board. Cannot handle changing number of probes between runs.

#### Start

- May 2019
- Original author: Isidor (Igi) Brkic
- Vivado version: 18.3
- RapidWright version: 2018.3.3 (installed 17 May 2018)
- Java version: 1.8.0_212
