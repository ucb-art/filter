// See LICENSE for license details.

package fir

import cde.Parameters
import chisel3._
import dsptools._
import dsptools.numbers._
import dspjunctions._
import dspblocks._
import ipxact._

class FIRBlock[T <: Data : Ring]()(implicit p: Parameters) extends DspBlock()(p) {
  def controls = Seq()
  def statuses = Seq()

  lazy val module = new FIRBlockModule[T](this)
  
  val config = p(FIRKey(p(DspBlockId)))
  (0 until config.numberOfTaps).map( i =>
    addControl(s"firCoeff$i", 0.U)
  )
  addStatus("firStatus")

}

class FIRBlockModule[T <: Data : Ring](outer: DspBlock)(implicit p: Parameters)
  extends GenDspBlockModule[T, T](outer)(p) with HasFIRParameters[T] {
  val module = Module(new FIR)
  val config = p(FIRKey(p(DspBlockId)))
  
  module.io.in <> unpackInput(lanesIn, genIn())
  unpackOutput(lanesOut, genOut()) <> module.io.out

  val taps = Wire(Vec(config.numberOfTaps, genTap.getOrElse(genIn())))
  val w = taps.zipWithIndex.map{case (x, i) => x.fromBits(control(s"firCoeff$i"))}
  module.io.taps := w
  status("firStatus") := module.io.out.sync

  IPXactComponents._ipxactComponents += DspIPXact.makeDspBlockComponent
}
