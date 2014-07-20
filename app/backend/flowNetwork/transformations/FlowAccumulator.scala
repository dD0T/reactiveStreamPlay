package backend.flowNetwork.transformations

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.{FlowObject, NumberObject}

object FlowAccumulator {
  var nodeType = "FlowAccumulator"
  def props(id: Long, name: String,  x: Int, y: Int): Props =
    Props(new FlowAccumulator(id, name, x, y))
}

class FlowAccumulator(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowAccumulator.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  var accumulator: Double = 0.0

  addConfigMapGetters(() => Map(
    "accumulator" -> accumulator.toString,
    "display" -> "accumulator"
  ))

  //TODO: Think about exposing this over the property interface in a sane way
  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsDouble(fieldOfInterest) match {
        case Some(value) =>
          accumulator += value
          target ! NumberObject(NextFlowUID(), o, accumulator)
          configUpdated() //FIXME: This shouldn't be updated all the time
        case None => log.debug(s"Message ${o.uid} doesn't have a Double convertible field $fieldOfInterest")
      }
  }
}
