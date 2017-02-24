package fir

import cde._
import chisel3._
import chisel3.experimental._
import craft._
import dsptools._
import dsptools.numbers.{Field=>_,_}
import dsptools.numbers.implicits._
import dspblocks._
import dspjunctions._
import dspblocks._
import _root_.junctions._
import uncore.tilelink._
import uncore.coherence._
import scala.collection.mutable.Map

object FIRConfigBuilder {
  def apply[T <: Data : Ring](
    id: String, firConfig: FIRConfig, genIn: () => T, genOut: Option[() => T] = None): Config = new Config(
      (pname, site, here) => pname match {
        case FIRKey(_id) if _id == id => firConfig
        case IPXactParameters(_id) if _id == id => {
          val parameterMap = Map[String, String]()
      
          // Conjure up some IPXACT synthsized parameters.
          val numTaps = firConfig.numberOfTaps
          val gk = site(GenKey(id))
          val inputLanes = gk.lanesIn
          val outputLanes = gk.lanesOut
          val inputTotalBits = gk.genIn.getWidth * inputLanes
          val outputTotalBits = gk.genOut.getWidth * outputLanes
          parameterMap ++= List(
            ("nTaps", numTaps.toString), 
            ("InputLanes", inputLanes.toString),
            ("InputTotalBits", inputTotalBits.toString), 
            ("OutputLanes", outputLanes.toString), 
            ("OutputTotalBits", outputTotalBits.toString),
            ("OutputPartialBitReversed", "1")
          )
      
          // add fractional bits if it's fixed point
          genIn() match {
            case fp: FixedPoint =>
              val fractionalBits = fp.binaryPoint
              parameterMap ++= List(
                ("InputFractionalBits", fractionalBits.get.toString)
              )
            case c: DspComplex[T] =>
              c.underlyingType() match {
                case "fixed" =>
                  val fractionalBits = c.real.asInstanceOf[FixedPoint].binaryPoint
                  parameterMap ++= List(
                    ("InputFractionalBits", fractionalBits.get.toString)
                  )
                case _ => 
              }
            case _ =>
          }
          genOut.getOrElse(genIn)() match {
            case fp: FixedPoint =>
              val fractionalBits = fp.binaryPoint
              parameterMap ++= List(
                ("OutputFractionalBits", fractionalBits.get.toString)
              )
            case c: DspComplex[T] =>
              c.underlyingType() match {
                case "fixed" =>
                  val fractionalBits = c.real.asInstanceOf[FixedPoint].binaryPoint
                  parameterMap ++= List(
                    ("OutputFractionalBits", fractionalBits.get.toString)
                  )
                case _ => 
              }
            case _ =>
          }

          // Coefficients
          //parameterMap ++= pfbConfig.window.zipWithIndex.map{case (coeff, index) => (s"FilterCoefficients$index", coeff.toString)}
          //parameterMap ++= List(("FilterScale", "1"))
      
          // tech stuff, TODO
          parameterMap ++= List(("ClockRate", "100"), ("Technology", "TSMC16nm"))
      
          parameterMap
        }
        case _ => throw new CDEMatchError
      }) ++
  ConfigBuilder.genParams(id, lanesIn_ = firConfig.lanesIn, lanesOut_ = Some(firConfig.lanesOut), genInFunc = genIn, genOutFunc = genOut)
  def standalone[T <: Data : Ring](id: String, firConfig: FIRConfig, genIn: () => T, genOut: Option[() => T] = None): Config =
    apply(id, firConfig, genIn, genOut) ++
    ConfigBuilder.buildDSP(id, {implicit p: Parameters => new FIRBlock[T]})
}

// default floating point and fixed point configurations
class DefaultStandaloneRealFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => DspReal()))
class DefaultStandaloneFixedPointFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => FixedPoint(32.W, 16.BP)))
class DefaultStandaloneComplexFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => DspComplex(FixedPoint(32.W, 16.BP), FixedPoint(32.W, 16.BP))))

// provides a sample custom configuration
class CustomStandaloneFIRConfig extends Config(FIRConfigBuilder.standalone(
  "fir", 
  FIRConfig(
    numberOfTaps = 16,
    pipelineDepth = 4,
    lanesIn = 32,
    lanesOut = 16), 
  genIn = () => DspComplex(FixedPoint(18.W, 16.BP), FixedPoint(18.W, 16.BP)),
  genOut = Some(() => DspComplex(FixedPoint(20.W, 16.BP), FixedPoint(20.W, 16.BP)))
))

case class FIRKey(id: String) extends Field[FIRConfig]

trait HasFIRParameters[T <: Data] extends HasGenParameters[T, T] {
   def genTap: Option[T] = None
}

case class FIRConfig(val numberOfTaps: Int = 8, val pipelineDepth: Int = 0, val lanesIn: Int = 8, val lanesOut: Int = 8) {
  // sanity checks
  require(lanesIn%lanesOut == 0, "Decimation amount must be an integer.")
  require(lanesOut <= lanesIn, "Cannot have more output lanes than input lanes.")
  require(pipelineDepth >= 0, "Must have positive pipelining")
}

