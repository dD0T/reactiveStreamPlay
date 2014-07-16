package backend.flowNetwork

import akka.actor.Props

object FlowSentiment {
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowSentiment(id, name, x, y))
}

class FlowSentiment(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, x, y) with TargetableFlow with FlowFieldOfInterest {

  // Utilizes http://sentiwordnet.isti.cnr.it/
  override def active: Receive = ???
}
