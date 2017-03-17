// See LICENSE for license details.
package fir

import dsptools._
import dsptools.numbers._
import dsptools.numbers.implicits._
import spire.algebra.{Field, Ring}
import breeze.math.{Complex}
import breeze.linalg._
import breeze.signal._
import breeze.signal.support._
import breeze.signal.support.CanFilter._
import chisel3._
import chisel3.util._
import diplomacy._
import chisel3.experimental._
import dspblocks._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
import org.scalatest.{FlatSpec, Matchers}
import scala.util.Random
import scala.math._
import org.scalatest.Tag

import cde._
import junctions._
import uncore.tilelink._
import uncore.coherence._


object LocalTest extends Tag("edu.berkeley.tags.LocalTest")

class FIRTester[T <: Data](dut: FIRBlockModule[T], input: Seq[Complex], coeffs: Seq[Complex], ref_output: Seq[Complex], verbose: Boolean = false)(implicit p: Parameters) extends DspBlockTester(dut) {
  val config = p(FIRKey(p(DspBlockId)))
  val gk = p(GenKey(p(DspBlockId)))
  val fgk = p(FIRGenKey(p(DspBlockId)))
  
  // define input datasets here, pad zeros
  if (input.size % gk.lanesIn != 0) {
    println("Warning: padding zeroes to align input data to input lanes")
  }
  val ins = (input ++ Seq.fill(input.size % gk.lanesIn)(Complex(0,0))).grouped(gk.lanesIn).map(Seq(_)).toSeq
  def streamIn = ins.map(packInputStream(_, gk.genIn))

  require(coeffs.size == config.numberOfTaps, "You passed more or fewer coefficients to the tester than the FIR has taps")

  pauseStream
  coeffs.zipWithIndex.foreach { case(x, i) => axiWriteAs(addrmap(s"Coefficient_$i"), x, fgk.genCoeff) }
  step(10)
  playStream
  step(ins.size)
  val output = unpackOutputStream(gk.genOut, gk.lanesOut)

  if (verbose) {
    println("Input")
    println(input.toArray.deep.mkString("\n"))
    println("Coefficients")
    println(coeffs.toArray.deep.mkString("\n"))
    println("Chisel Output")
    println(output.toArray.deep.mkString("\n"))
    println("Reference Output")
    println(ref_output.toArray.deep.mkString("\n"))
  }

  if (ref_output.size > 0) {
    require(ref_output.size == gk.lanesOut, "Error: reference output wrong size")
    output.zip(ref_output).foreach { case (a, b) => require(a == b, "Error: Wrong output!") }
  }

}

class FIRWrapperSpec extends FlatSpec with Matchers {
  behavior of "FIR"
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "firrtl", testerSeed = 7L)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
  }

  it should "work with DspBlockTester" in {
    implicit val p: Parameters = Parameters.root(FIRConfigBuilder.standalone(
      id = "fir", 
      firConfig = FIRConfig(
        numberOfTaps = 4,
        processingDelay = 0,
        lanesIn = 4,
        lanesOut = 2), 
      genIn = () => DspComplex(FixedPoint(8.W, 7.BP), FixedPoint(8.W, 7.BP)),
      genOut = Some(() => DspComplex(FixedPoint(8.W, 10.BP), FixedPoint(8.W, 10.BP))),
      genCoeff = Some(() => DspComplex(FixedPoint(11.W, 10.BP), FixedPoint(11.W, 10.BP))))
      .toInstance)
    val dut = () => LazyModule(new FIRBlock[DspComplex[FixedPoint]]).module
    val input = Seq.fill(16)(Complex(0.125, 0.0))
    val coeffs = Seq.fill(4)(Complex(0.125, 0.0))
    chisel3.iotesters.Driver.execute(dut, manager) { c => new FIRTester(c, input, coeffs, Seq(), true) } should be (true)
  }

  it should "not have a DC bias from truncating" in {
    implicit val p: Parameters = Parameters.root(FIRConfigBuilder.standalone(
      id = "fir", 
      firConfig = FIRConfig(
        numberOfTaps = 128,
        processingDelay = 0,
        lanesIn = 4,
        lanesOut = 2), 
      genIn = () => DspComplex(FixedPoint(8.W, 7.BP), FixedPoint(8.W, 7.BP)),
      genOut = Some(() => DspComplex(FixedPoint(8.W, 10.BP), FixedPoint(8.W, 10.BP))),
      genCoeff = Some(() => DspComplex(FixedPoint(11.W, 10.BP), FixedPoint(11.W, 10.BP))))
      .toInstance)
    val dut = () => LazyModule(new FIRBlock[DspComplex[FixedPoint]]).module
    val input = Seq.fill(128*4)(Complex(0.015625, 0.0))
    val coeffs = Seq.fill(128)(Complex(0.00390625, 0.0))
    val output = Seq.fill(2)(Complex(0.0078125, 0))
    chisel3.iotesters.Driver.execute(dut, manager) { c => new FIRTester(c, input, coeffs, output, true) } should be (true)
  }
}
