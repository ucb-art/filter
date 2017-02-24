// See LICENSE for license details.
package fir

import dsptools.numbers.implicits._
import dsptools.Utilities._
import dsptools.{DspContext, Grow}
import spire.algebra.{Field, Ring}
import breeze.math.{Complex}
import breeze.linalg._
import breeze.signal._
import breeze.signal.support._
import breeze.signal.support.CanFilter._
import chisel3._
import chisel3.util._
import chisel3.iotesters._
import firrtl_interpreter.InterpreterOptions
import dsptools.numbers.{DspReal, SIntOrder, SIntRing}
import dsptools.{DspContext, DspTester, Grow}
import org.scalatest.{FlatSpec, Matchers}
import dsptools.numbers.implicits._
import dsptools.numbers.{DspComplex, Real}
import scala.util.Random
import scala.math._
import org.scalatest.Tag

import cde._
import junctions._
import uncore.tilelink._
import uncore.coherence._

import dsptools._

object LocalTest extends Tag("edu.berkeley.tags.LocalTest")

class FIRWrapperTester[T <: Data](c: FIRWrapper[T])(implicit p: Parameters) extends DspBlockTester(c) {
  val config = p(FIRKey)(p)
  val gk = p(GenKey)
  val test_length = 10
  
  // define input datasets here
  val input = Array.fill(test_length)(Array.fill(gk.lanesIn)(Random.nextDouble*2-1))
  def rawStreamIn = input
  val filter_coeffs = Array.fill(config.numberOfTaps)(Random.nextDouble*2-1)

  def doublesToBigInt(in: Array[Double]): BigInt = {
    in.reverse.foldLeft(BigInt(0)) {case (bi, dbl) =>
      val new_bi = BigInt(java.lang.Double.doubleToLongBits(dbl))
      (bi << 64) + new_bi
    }
  }
  def streamIn = rawStreamIn.map(doublesToBigInt)

  // use Breeze FIR filter, but trim (it zero pads the input) and decimate output
  val expected_output = filter(DenseVector(rawStreamIn.flatten), DenseVector(filter_coeffs)).toArray.drop(config.numberOfTaps-2).dropRight(config.numberOfTaps-2).grouped(gk.lanesIn/gk.lanesOut).map(_.head).toArray

  pauseStream
  //println("Addr Map:")
  //println(testchipip.SCRAddressMap("FIRWrapper").get.map(_.toString).toString)
  // assumes coefficients are first addresses
  filter_coeffs.zipWithIndex.foreach { case(x, i) => axiWrite(i*8, doubleToBigIntBits(x)) }
  step(10)
  playStream
  step(test_length)
  val output = streamOut.map { x => (0 until gk.lanesOut).map { idx => {
    val y = (x >> (64 * idx)) & 0xFFFFFFFFFFFFFFFFL
    java.lang.Double.longBitsToDouble(y.toLong)
  }}}

  //println("Input")
  //println(rawStreamIn.flatten.deep.mkString(","))
  //println("Coefficients")
  //println(filter_coeffs.deep.mkString(","))
  //println("Chisel Output")
  //println(output.toArray.flatten.deep.mkString(","))
  //println("Reference Output")
  //println(expected_output.deep.mkString(","))

  output.flatten.zip(expected_output).zipWithIndex.foreach { case((chisel, ref), index) => 
    if (chisel != ref) {
      val epsilon = 1e-12
      val err = abs(chisel-ref)/abs(ref+epsilon)
      assert(err < epsilon || ref < epsilon, s"Error: mismatch on output $index of ${err*100}%\n\tReference: $ref\n\tChisel:    $chisel")
    }
  }
}

class FIRWrapperSpec extends FlatSpec with Matchers {
  behavior of "FIRWrapper"
  val manager = new TesterOptionsManager {
    testerOptions = TesterOptions(backendName = "verilator", testerSeed = 7L)
    interpreterOptions = InterpreterOptions(setVerbose = false, writeVCD = true)
  }

  it should "work with DspBlockTester" in {
    implicit val p: Parameters = Parameters.root(new DspConfig().toInstance)
    val dut = () => new FIRWrapper[DspReal]()
    chisel3.iotesters.Driver.execute(dut, manager) { c => new FIRWrapperTester(c) } should be (true)
  }
}
