package backend.flowNetwork.sources

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{TickingFlow, FlowNode, TargetableFlow}
import backend.flowTypes.TwitterMessage
import external.LoremIpsum

object FlowIpsumSource {
  var nodeType = "IpsumSource"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowIpsumSource(id, name, x, y))
}

/**
 * Generates a stream of random twitter LoremIpsum messages.
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
class FlowIpsumSource(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowIpsumSource.nodeType, x, y, 1, 0) with TargetableFlow with TickingFlow {

  var count: Long = 0

  addConfigMapGetters(() => Map(
    "#sourced" -> count.toString,
    "display" -> ("#sourced," + tickPropName)
  ))

  override def passive = {
    case Tick =>
  }

  override def active = {
    case Tick =>
      target ! TwitterMessage(NextFlowUID(), LoremIpsum.randomWord, LoremIpsum.sentence, "Latinlike")
      count += 1
      configUpdated()
  }

}
