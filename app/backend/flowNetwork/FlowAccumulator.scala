package backend.flowNetwork

import akka.actor.Props
import backend.NextFlowUID
import backend.flowTypes.{NumberObject, FlowObject}

object FlowAccumulator {
  def props(): Props = Props(new FlowAccumulator)
}

class FlowAccumulator extends TargetableFlow with FlowFieldOfInterest {
  var accumulator: Double = 0.0

  def common: Receive = handleFieldOfInterest

  override def passive: Receive = common
  override def active: Receive = common orElse {
    case o: FlowObject =>
      o.contentAsDouble(fieldOfInterest) match {
        case Some(value) =>
          accumulator += value
          target ! NumberObject(NextFlowUID(), o, accumulator)
        case None => log.debug(s"Message ${o.uid} doesn't have a Double convertible field $fieldOfInterest")
      }
  }
}
