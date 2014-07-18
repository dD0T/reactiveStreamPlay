package backend.flowNetwork

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.json._

object MessageTranslator {
  def props(chan: Channel[JsValue]): Props = Props(new MessageTranslator(chan))
}

class MessageTranslator(val chan: Channel[JsValue]) extends Actor with ActorLogging {
  override def receive: Receive = {
    case (id: Long, Configuration(data)) =>
      // Node configuration update
      chan.push(Json.obj("id" -> id.toString,
                         "config" -> Json.toJson(data)))

    case (id: Long, None) =>
      // Node deletion
      chan.push(Json.obj("id" -> id.toString,
                        "config" -> Json.obj(),
                        "deleted" -> "1"))
  }
}
