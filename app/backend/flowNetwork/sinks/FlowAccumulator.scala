package backend.flowNetwork.sinks

import akka.actor.Props
import backend.flowNetwork.{FlowFieldOfInterest, FlowNode}
import backend.flowTypes.FlowObject

object FlowAccumulator {
  var nodeType = "Accumulator"
  def props(id: Long, name: String,  x: Int, y: Int): Props =
    Props(new FlowAccumulator(id, name, x, y))
}

/**
 * Adds up received values in FOI as doubles.
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
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
