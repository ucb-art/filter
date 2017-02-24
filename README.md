DSP Project Template
=======================

You've done the chisel [tutorials](https://github.com/ucb-bar/chisel-tutorial.git), and now you 
are ready to start your own dsp project.  The following procedure should get you started
with a clean running [dsptools](https://github.com/ucb-bar/dsptools.git) project.

## Make your own DSP project
### How to get started
The first thing you want to do is clone this repo into a directory of your own.  I'd recommend creating a dsp projects directory somewhere
```sh
mkdir ~/DspProjects
cd ~/DspProjects

git clone https://github.com/ucb-art/dsp-template.git MyChiselProject
cd MyChiselProject
```

The next step is to build and install all the dependencies. Run the following from MyChiselProject/

```
git submodule update --init
cd dsp-framework
./update.bash
cd ../
make libs
```

It should build and install the needed dependencies without error.
If you do not have verilator installed, you should [follow instructions here](https://github.com/ucb-bar/chisel3) under the 'Install Verilator' heading.

### Make your project into a fresh git repo
There may be more elegant way to do it, but the following works for me. **Note:** this project comes with a magnificent 339 line (at this writing) .gitignore file.
 You may want to edit that first in case we missed something, whack away at it, or start it from scratch.
```sh
rm -rf .git
git init
git add .gitignore *
git commit -m 'Starting MyDspProject'
```
Connecting this up to github or some other remote host is an exercise left to the reader.
### Did it work?
You should now have a project based on Chisel3 that can be run.  **Note:** With a nod to cargo cult thinking, some believe 
it is best to execute the following sbt before opening up this directory in your IDE. I have no formal proof of this assertion.
So go for it, at the command line in the project root.
```sh
make test
```
You should see a whole bunch of output that ends with the following lines
```
inChannelName: 00018143.in
outChannelName: 00018143.out
cmdChannelName: 00018143.cmd
STARTING test_run_dir/fir.FIRWrapperSpec347336104/VFIRWrapper
SEED 1481676096621
Enabling waves..
Exit Code: 0
RAN 40 CYCLES PASSED
[info] FIRWrapperSpec:
[info] FIRWrapper
[info] - should work with DspBlockTester
[info] ScalaCheck
[info] Passed: Total 0, Failed 0, Errors 0, Passed 0
[info] ScalaTest
[info] Run completed in 8 seconds, 799 milliseconds.
[info] Total number of tests run: 1
[info] Suites: completed 1, aborted 0
[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
[info] All tests passed.
[info] Passed: Total 1, Failed 0, Errors 0, Passed 1
[success] Total time: 26 s, completed Dec 13, 2016 4:41:41 PM
```
If you see the above then...
### It worked!
You are ready to go. We have a few recommended practices and things to do.
* Use packages and following conventions for [structure](http://www.scala-sbt.org/0.13/docs/Directories.html) and [naming](http://docs.scala-lang.org/style/naming-conventions.html)
* Package names should be clearly reflected in the testing hierarchy
* Build tests for all your work.
 * This template includes a dependency on the Chisel3 IOTesters, this is a reasonable starting point for most tests
 * You can remove this dependency in the build.sbt file if necessary
* Change the name of your project in the build.sbt
* Change your README.md

## Development/Bug Fixes
This is the release version of dsp-template. If you have bug fixes or
changes you would like to see incorporated in this repo, please checkout
the master branch and submit pull requests against it.

