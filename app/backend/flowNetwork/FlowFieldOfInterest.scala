package backend.flowNetwork

trait FlowFieldOfInterest extends FlowNode {
  var fieldOfInterest: String = "default"

  addConfigSetters({
    case ("field", foi) =>
      log.info(s"Updating FOI to $foi")
      fieldOfInterest = foi
  })

  addConfigMapGetters(() => Map(
    "field" -> fieldOfInterest
  ))
}
