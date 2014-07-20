package backend.flowNetwork

import scala.concurrent.duration._

trait TickingFlow extends FlowNode {
  case object Tick

  import context.dispatcher

  private var tick = context.system.scheduler.schedule(1 second, 1 second, self, Tick)
  var tickFrequency = 1.0
  val tickPropName = "freq"

  addConfigMapGetters(() => Map(
    tickPropName -> tickFrequency.toString
  ))

  addConfigSetters({
    case (`tickPropName`, f) =>
      log.info(s"Changing frequency to $f")
      tickFrequency = f.toDouble

      tick.cancel()
      if (tickFrequency > 0)
        tick = context.system.scheduler.schedule(0 seconds, (1.0 / tickFrequency) second, self, Tick)
  })

  override def postStop() =
    tick.cancel()
    super.postStop()
}
