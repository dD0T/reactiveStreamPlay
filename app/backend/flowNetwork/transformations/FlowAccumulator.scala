package backend.flowNetwork.transformations

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.{FlowObject, NumberObject}

object FlowAccumulator {
  var nodeType = "Accumulator"
  def props(id: Long, name: String,  x: Int, y: Int): Props =
    Props(new FlowAccumulator(id, name, x, y))
}

class FlowAccumulator(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowAccumulator.nodeType, x, y, 0, 1) with FlowFieldOfInterest {

  var accumulator: Double = 0.0

  addConfigMapGetters(() => Map(
    "accumulator" -> accumulator.toString,
    "display" -> "accumulator"
  ))

  override def receive: Receive = super.receive orElse {
    case o: FlowObject =>
      o.contentAsDouble(fieldOfInterest) match {
        case Some(value) =>
          accumulator += value
          configUpdated()

        case None => log.debug(s"Message ${o.uid} doesn't have a Double convertible field $fieldOfInterest")
      }
  }
}
