package backend.flowNetwork.transformations

import akka.actor.Props
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.FlowObject

object FlowFilter {
  var nodeType = "Filter"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowFilter(id, name, x, y))
}

class FlowFilter(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowFilter.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  var filter: String = ".*"
  var dropped: Int = 0

  addConfigSetters({
    case ("filter", f) =>
      log.info(s"Updating filter to '$f'")
      filter = f
  })

  addConfigMapGetters(() => Map(
    "filter" -> filter,
    "dropped" -> dropped.toString(),
    "display" -> "filter,dropped"
  ))

  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(value) =>
          if (value matches filter) target ! o
          else dropped += 1
          configUpdated()
        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
