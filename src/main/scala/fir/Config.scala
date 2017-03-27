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
    id: String, firConfig: FIRConfig, genIn: () => T, genOut: Option[() => T] = None, genCoeffFunc: Option[() => T] = None): Config = new Config(
      (pname, site, here) => pname match {
        case FIRKey(_id) if _id == id => firConfig
        case FIRGenKey(_id) if _id == id => new FIRGenParameters {
          def genCoeff[T <: Data] = genCoeffFunc.getOrElse(genOut.getOrElse(genIn))().asInstanceOf[T]
        }
        case IPXactParameters(_id) if _id == id => {
          val parameterMap = Map[String, String]()
      
          // Conjure up some IPXACT synthsized parameters.
          val numTaps = firConfig.numberOfTaps
          val config = site(FIRKey(id))
          val gk = site(GenKey(id))
          val inputLanes = gk.lanesIn
          val outputLanes = gk.lanesOut
          parameterMap ++= List(
            ("InputLanes", inputLanes.toString),
            ("OutputLanes", outputLanes.toString)
          )
      
          // add fractional bits if it's fixed point
          genIn() match {
            case fp: FixedPoint =>
              val fractionalBits = fp.binaryPoint
              parameterMap ++= List(
                ("InputFractionalBits", fractionalBits.get.toString),
                ("InputTotalBits", fp.getWidth.toString)
              )
            case c: DspComplex[T] =>
              parameterMap ++= List(
                ("InputTotalBits", c.real.getWidth.toString) // assume real and imag have equal total widths
              )
              c.underlyingType() match {
                case "fixed" =>
                  val fractionalBits = c.real.asInstanceOf[FixedPoint].binaryPoint
                  parameterMap ++= List(
                    ("InputFractionalBits", fractionalBits.get.toString)
                  )
                case _ => 
              }
            case d: DspReal =>
              parameterMap ++= List(
                ("InputTotalBits", d.getWidth.toString)
              )
            case s: SInt => 
              parameterMap ++= List(
                ("InputTotalBits", s.getWidth.toString)
              )
            case _ =>
              throw new DspException("Unknown input type for filter")
          }

          genOut.getOrElse(genIn)() match {
            case fp: FixedPoint =>
              val fractionalBits = fp.binaryPoint
              parameterMap ++= List(
                ("OutputFractionalBits", fractionalBits.get.toString),
                ("OutputTotalBits", fp.getWidth.toString)
              )
            case c: DspComplex[T] =>
              parameterMap ++= List(
                ("OutputTotalBits", c.real.getWidth.toString) // assume real and imag have equal total widths
              )
              c.underlyingType() match {
                case "fixed" =>
                  val fractionalBits = c.real.asInstanceOf[FixedPoint].binaryPoint
                  parameterMap ++= List(
                    ("OutputFractionalBits", fractionalBits.get.toString)
                  )
                case _ => 
              }
            case d: DspReal =>
              parameterMap ++= List(
                ("OutputTotalBits", d.getWidth.toString)
              )
            case s: SInt => 
              parameterMap ++= List(
                ("OutputTotalBits", s.getWidth.toString)
              )
            case _ =>
              throw new DspException("Unknown output type for filter")
          }

          // Coefficients
          genCoeffFunc.getOrElse(genOut.getOrElse(genIn))() match {
            case fp: FixedPoint =>
              val fractionalBits = fp.binaryPoint
              parameterMap ++= List(
                ("CoefficientFractionalBits", fractionalBits.get.toString),
                ("CoefficientTotalBits", fp.getWidth.toString)
              )
            case c: DspComplex[T] =>
              parameterMap ++= List(
                ("CoefficientTotalBits", c.real.getWidth.toString) // assume real and imag have equal total widths
              )
              c.underlyingType() match {
                case "fixed" =>
                  val fractionalBits = c.real.asInstanceOf[FixedPoint].binaryPoint
                  parameterMap ++= List(
                    ("CoefficientFractionalBits", fractionalBits.get.toString)
                  )
                case _ => 
              }
            case d: DspReal =>
              parameterMap ++= List(
                ("CoefficientTotalBits", d.getWidth.toString)
              )
            case s: SInt => 
              parameterMap ++= List(
                ("CoefficientTotalBits", s.getWidth.toString)
              )
            case _ =>
              throw new DspException("Unknown coefficient type for filter")
          }
      
          // tech stuff, TODO
          parameterMap ++= List(("ClockRate", "100"), ("Technology", "TSMC16nm"),
            ("NumCoefficients", config.numberOfTaps.toString),
            ("ProcessingDelay", config.processingDelay.toString)
          )
      
          parameterMap
        }
        case _ => throw new CDEMatchError
      }) ++
  ConfigBuilder.genParams(id, lanesIn_ = firConfig.lanesIn, lanesOut_ = Some(firConfig.lanesOut), genInFunc = genIn, genOutFunc = genOut)
  def standalone[T <: Data : Ring](id: String, firConfig: FIRConfig, genIn: () => T, genOut: Option[() => T] = None, genCoeff: Option[() => T] = None): Config =
    apply(id, firConfig, genIn, genOut, genCoeff) ++
    ConfigBuilder.buildDSP(id, {implicit p: Parameters => new FIRBlock[T]})
}

// default floating point and fixed point configurations, and complex
class DefaultStandaloneRealFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => DspReal()))
class DefaultStandaloneFixedPointFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => FixedPoint(32.W, 16.BP)))
class DefaultStandaloneComplexFIRConfig extends Config(FIRConfigBuilder.standalone("fir", FIRConfig(), () => DspComplex(FixedPoint(32.W, 16.BP), FixedPoint(32.W, 16.BP))))

// provides a sample custom configuration
class CustomStandaloneFIRConfig extends Config(FIRConfigBuilder.standalone(
  "fir", 
  FIRConfig(
    numberOfTaps = 136,
    processingDelay = 2,
    lanesIn = 32,
    lanesOut = 4,
    multiplyPipelineDepth = 1,
    outputPipelineDepth = 5), 
  genIn = () => DspComplex(FixedPoint(8.W, 7.BP), FixedPoint(8.W, 7.BP)),
  genOut = Some(() => DspComplex(FixedPoint(11.W, 10.BP), FixedPoint(11.W, 10.BP))),
  genCoeff = Some(() => DspComplex(FixedPoint(8.W, 10.BP), FixedPoint(8.W, 10.BP)))
))

case class FIRKey(id: String) extends Field[FIRConfig]
case class FIRGenKey(id: String) extends Field[FIRGenParameters]

trait FIRGenParameters {
   def genCoeff[T<:Data]: T
}

// T = input, V = output and coeff
trait HasFIRGenParameters[T <: Data, V <: Data] extends HasGenParameters[T, V] {
  def firGenExternal = p(FIRGenKey(p(DspBlockId)))
  def genCoeff(dummy: Int = 0) = firGenExternal.genCoeff[V]
}

case class FIRConfig(val numberOfTaps: Int = 8, val processingDelay: Int = 0, val lanesIn: Int = 8, val lanesOut: Int = 8, val multiplyPipelineDepth: Int = 1, val outputPipelineDepth: Int = 1) {
  // sanity checks
  require(lanesIn%lanesOut == 0, "Decimation amount must be an integer.")
  require(lanesOut <= lanesIn, "Cannot have more output lanes than input lanes.")
  require(processingDelay >= 0, "Must have positive processing delay")
  require(numberOfTaps > 0, "Must have some taps")
  require(multiplyPipelineDepth >= 0, "Must have positiving multiply pipeline depth")
  require(outputPipelineDepth >= 0, "Must have positiving output pipeline depth")
  //require((numberOfTaps-(lanesIn/lanesOut))%(lanesIn*2) == 0, "Number of taps must satisfy 2*n*InputLanes + (InputLanes/OutputLanes) where n is an integer")
}

