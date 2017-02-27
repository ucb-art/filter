package fir

import cde._
import chisel3._
import chisel3.util._
import dspjunctions.ValidWithSync
import dsptools.numbers._
import dsptools.numbers.implicits._
import dsptools.counters._
import dspblocks._
import scala.math._

class FIRIO[T<:Data:Ring]()(implicit val p: Parameters) extends Bundle with HasFIRGenParameters[T, T] {
  val config = p(FIRKey(p(DspBlockId)))
  val in = Input(ValidWithSync(Vec(config.lanesIn, genIn())))
  val out = Output(ValidWithSync(Vec(config.lanesOut, genOut())))

  val data_set_end_status = Output(Bool())
  val data_set_end_clear = Input(Bool())

  val taps = Input(Vec(config.numberOfTaps, genCoeff()))
}

class FIR[T<:Data:Ring]()(implicit val p: Parameters) extends Module with HasFIRGenParameters[T, T] {
  val io = IO(new FIRIO[T])
  val config = p(FIRKey(p(DspBlockId)))

  // define the latency as the slowest output
  val latency = config.processingDelay
  io.out.sync := ShiftRegisterWithReset(io.in.sync, latency, 0.U)
  io.out.valid := ShiftRegisterWithReset(io.in.valid, latency, 0.U)

  // feed in zeros when invalid
  val in = Wire(Vec(config.lanesIn, genIn()))
  when (io.in.valid) {
    in := io.in.bits
  } .otherwise {
    in := Vec.fill(config.lanesIn)(Ring[T].zero)
  }

  // data set end flag
  val valid_delay = Reg(next=io.out.valid)
  val dses = Reg(init=false.B)
  when (io.data_set_end_clear) {
    dses := false.B
  } .elsewhen (valid_delay & ~io.out.valid) {
    dses := true.B
  }
  io.data_set_end_status := dses


  // calculate products as in * tap
  val products = io.taps.reverse.map { tap => in.map { i => 
    i * tap
  }}

  // rotates a Seq by i terms, wraps around and can be negative for reverse rotation
  // e.g. (1,2,3) rotate by 1 = (2,3,1)
  def rotate(l: Seq[T], i: Int): Seq[T] = if(i >= 0) { l.drop(i%l.size) ++ l.take(i%l.size) } else { l.takeRight(-i%l.size) ++ l.dropRight(-i%l.size) }

  // rotate through, handling weird combinations of taps and input/output lanes
  val last = products.reduceLeft { (left: Seq[T], right: Seq[T]) =>
    val reg = Reg(left.last.cloneType)
    reg := left.last
    right.zip(rotate(left.dropRight(1) :+ reg, -1)).map{case(a, b) => a+b}
  }

  // all pipeline registers tacked onto end, hopefully synthesis tools handle correctly
  io.out.bits := Vec(last.grouped(config.lanesIn/config.lanesOut).map(_.head).toSeq)
}
