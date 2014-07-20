package backend.flowNetwork

import akka.actor.Props
import backend.flowTypes.FlowObject


object FlowCounter {
  var nodeType = "FlowCounter"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowCounter(id, name, x, y))
}

class FlowCounter(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowIpsumSource.nodeType, x, y, 0, 1) {

  var count = 0

  addConfigMapGetters(() => Map(
    "count" -> count.toString,
    "display" -> "count"
  ))

  override def receive: Receive = super.receive orElse {
    case _: FlowObject =>
      count += 1
      configUpdated()
  }
}