package backend.flowNetwork.transformations

import akka.actor.Props
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}

object FlowSentiment {
  var nodeType = "FlowSentiment"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowSentiment(id, name, x, y))
}

class FlowSentiment(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowSentiment.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  // Utilizes http://sentiwordnet.isti.cnr.it/
  override def active: Receive = {
    case _ => // Do nothing at all ;)
  }
}
