package api

import akka.actor.ActorSystem
import spray.routing.HttpService

import core.Core

trait Api extends HttpService with Core {
  implicit def system: ActorSystem

  val routes =
    (new StaticService).route
}
