package backend.flowNetwork.transformations

import akka.actor.{ActorRef, Props}
import backend.flowNetwork.{FlowNode, AddTarget, RemoveTarget}
import backend.flowTypes.FlowObject

object FlowCrossbar {
  var nodeType = "Crossbar"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowCrossbar(id, name, x, y))
}

/**
 * Takes input from multiple sources and copies it to multiple targets.
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
class FlowCrossbar(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowCrossbar.nodeType, x, y, 3 , 3) {

  var targets = Set[ActorRef]()

  addConfigMapGetters(() => Map(
    "active" -> (if (targets.size > 0) "1" else "0"),
    "targets" -> targets.size.toString
  ))

  override def receive = super.receive orElse {
    case o: FlowObject =>
      targets.map(_ ! o)

    case AddTarget(t) =>
      log.info(s"New target $t on $this")
      targets += t
      configUpdated()

    case RemoveTarget(t) =>
      if (targets contains t) {
        log.info(s"Removed target $t on $this")
        targets -= t
        configUpdated()
      }
  }
}
