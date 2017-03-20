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

  addStatus("Data_Set_End_Status")
  addControl("Data_Set_End_Clear", 0.U)
  
  val config = p(FIRKey(p(DspBlockId)))
  (0 until config.numberOfTaps).map( i =>
    addControl(s"Coefficient_$i", 0.U)
  )

}

class FIRBlockModule[T <: Data : Ring](outer: DspBlock)(implicit p: Parameters)
  extends GenDspBlockModule[T, T](outer)(p) with HasFIRGenParameters[T, T] {
  val module = Module(new FIR)
  val config = p(FIRKey(p(DspBlockId)))
  
  module.io.in <> unpackInput(lanesIn, genIn())
  unpackOutput(lanesOut, genOut()) <> module.io.out

  status("Data_Set_End_Status") := module.io.data_set_end_status
  module.io.data_set_end_clear := control("Data_Set_End_Clear")

  val taps = Wire(Vec(config.numberOfTaps, genCoeff()))
  val w = taps.zipWithIndex.map{case (x, i) => x.fromBits(control(s"Coefficient_$i"))}
  module.io.taps := w

  IPXactComponents._ipxactComponents += DspIPXact.makeDspBlockComponent(baseAddr, uuid, this.name)
}
