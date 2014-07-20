package backend.flowNetwork.sources

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{TickingFlow, FlowNode, TargetableFlow}
import backend.flowTypes.NumberObject

import scala.util.Random

object FlowNumberSource {
  var nodeType = "NumberSource"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowNumberSource(id, name, x, y))
}

class FlowNumberSource(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowNumberSource.nodeType, x, y, 1, 0) with TargetableFlow with TickingFlow {

  var count: Long = 0

  addConfigMapGetters(() => Map(
    "#sourced" -> count.toString,
    "display" -> ("#sourced," + tickPropName)
  ))

  override def passive = {
    case Tick => // Nothing
  }

  override def active = {
    case Tick =>
      target ! NumberObject(NextFlowUID(), 0, Random.nextDouble())
      count += 1
      configUpdated()
  }
}
