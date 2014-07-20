package backend.flowNetwork.sources

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, TargetableFlow}
import backend.flowTypes.NumberObject

import scala.concurrent.duration._
import scala.util.Random

object FlowNumberSource {
  var nodeType = "NumberSource"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowNumberSource(id, name, x, y))
}

class FlowNumberSource(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowNumberSource.nodeType, x, y, 1, 0) with TargetableFlow {

  import context.dispatcher

  private case object Tick

  val tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)
  var count: Long = 0

  addConfigMapGetters(() => Map(
    "sourced" -> count.toString,
    "display" -> "sourced"
  ))

  override def postStop() = tick.cancel()

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
