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

class FIRIO[T<:Data:Ring]()(implicit val p: Parameters) extends Bundle with HasFIRParameters[T] {
  val config = p(FIRKey(p(DspBlockId)))
  val in = Input(ValidWithSync(Vec(config.lanesIn, genIn())))
  val out = Output(ValidWithSync(Vec(config.lanesOut, genOut())))
  val taps = Input(Vec(config.numberOfTaps, genTap.getOrElse(genIn()))) // default to input or output?
}

class FIR[T<:Data:Ring]()(implicit val p: Parameters) extends Module with HasFIRParameters[T] {
  val io = IO(new FIRIO[T])
  val config = p(FIRKey(p(DspBlockId)))

  // define the latency as the slowest output
  val latency = ceil(config.numberOfTaps/config.lanesIn).toInt + config.pipelineDepth
  io.out.sync := ShiftRegister(io.in.sync, latency)
  io.out.valid := ShiftRegister(io.in.valid, latency)

  // feed in zeros when invalid
  val in = Wire(Vec(config.lanesIn, genIn()))
  when (io.in.valid) {
    in := io.in.bits
  } .otherwise {
    in := Vec.fill(config.lanesIn)(Ring[T].zero)
  }
  val products = io.taps.reverse.map { tap => in.map { i => 
    i * tap
  }}

  // rotates a Seq by i terms, wraps around and can be negative for reverse rotation
  // e.g. (1,2,3) rotate by 1 = (2,3,1)
  def rotate(l: Seq[T], i: Int): Seq[T] = if(i >= 0) { l.drop(i%l.size) ++ l.take(i%l.size) } else { l.takeRight(-i%l.size) ++ l.dropRight(-i%l.size) }

  val last = products.reduceLeft { (left: Seq[T], right: Seq[T]) =>
    val reg = Reg(left.last.cloneType)
    reg := left.last
    right.zip(rotate(left.dropRight(1) :+ reg, -1)).map{case(a, b) => a+b}
  }

  // all pipeline registers tacked onto end, hopefully synthesis tools handle correctly
  io.out.bits := ShiftRegister(Vec(last.grouped(config.lanesIn/config.lanesOut).map(_.head).toSeq), config.pipelineDepth)
}
