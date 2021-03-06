package backend.flowNetwork.sinks

import akka.actor.Props
import backend.flowNetwork.FlowNode
import backend.flowTypes.FlowObject
import play.api.libs.json.Json


object FlowTrace {
  var nodeType = "Trace"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowTrace(id, name, x, y))
}

/**
 * Displays the last n received messages (default = 1).
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
class FlowTrace(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowTrace.nodeType, x, y, 0, 1) {

  var depth = 1
  var history = scala.collection.mutable.Queue.empty[FlowObject]

  addConfigMapGetters(() => Map(
    "active" -> "1",
    "depth" -> depth.toString,
    "history" -> historyAsJsonString,
    "#stored" -> history.length.toString,
    "display" -> "#stored,depth"
  ))

  addConfigSetters({
    case ("depth", d) =>
      log.info(s"Changing trace depth to $depth")
      depth = d.toInt
      while (history.length > depth)
        history.dequeue
  })

  def historyAsJsonString: String =
    Json.toJson(history map { o => o.asStringMap }).toString

  override def receive: Receive = super.receive orElse {
    case o: FlowObject =>
      history += o

      if (history.length > depth)
        history.dequeue

      configUpdated()
  }
}