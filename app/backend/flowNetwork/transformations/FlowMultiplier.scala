package backend.flowNetwork.transformations


import akka.actor.Props
import backend.flowNetwork.{FlowNode, TargetableFlow}
import backend.flowTypes.FlowObject


object FlowMultiplier {
  var nodeType = "Multiplier"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowMultiplier(id, name, x, y))
}

class FlowMultiplier(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowMultiplier.nodeType, x, y, 1, 1) with TargetableFlow {

  var factor: Int = 2

  addConfigSetters({
    case ("factor", f) =>
      factor = f.toInt
      log.info(s"Updating multiplier factor to '$f'")
  })

  addConfigMapGetters(() => Map(
    "factor" -> factor.toString,
    "display" -> "factor"
  ))

  override def active: Receive = {
    case o: FlowObject =>
      for (i <- 1 to factor) target ! o //FIXME: This will duplicate UIDs which should be a big nono. We don't really care at the moment
  }
}