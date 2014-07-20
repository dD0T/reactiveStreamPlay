package backend.flowNetwork

import backend.flowNetwork.FlowNode

trait FlowFieldOfInterest extends FlowNode {
  var fieldOfInterest: String = "default"

  addConfigSetters({
    case ("fieldOfInterest", foi) =>
      log.info(s"Updating FOI to $foi")
      fieldOfInterest = foi
  })

  addConfigMapGetters(() => Map(
    "fieldOfInterest" -> fieldOfInterest
  ))
}
