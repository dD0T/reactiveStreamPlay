package backend.flowNetwork

import akka.actor.Props

object FlowSentiment {
  def props(): Props = Props(new FlowSentiment)
}

class FlowSentiment extends TargetableFlow with FlowFieldOfInterest {
  // Utilizes http://sentiwordnet.isti.cnr.it/
  override def active: Receive = ???
}
