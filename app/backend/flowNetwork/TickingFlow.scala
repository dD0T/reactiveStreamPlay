package backend.flowNetwork

import scala.concurrent.duration._

/**
 * Extends a FlowNode with a configurable Tick rate.
 *
 * When extended with this a FlowNode will receive
 * Tick case objects in a configurable frequency. By
 * default 1 Hz.
 *
 * The frontend can reconfigure this via the exposed
 * configuration property "freq".
 *
 * @note When a too high frequency is set the internal
 *       timer resolution might not be sufficient to produce
 *       Tick messages at the requested rate.
 */
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
