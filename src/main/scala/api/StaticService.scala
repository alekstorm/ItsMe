package api

import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.json._
import spray.routing.Directives

class StaticService(implicit system: ActorSystem) extends Directives with DefaultJsonFormats {
  val route =
    pathSingleSlash {
      getFromFile("/Users/astorm/voice/static/index.html")
    } ~
    path("register") {
      getFromFile("/Users/astorm/voice/static/register.html")
    } ~
    path("recorder.js") {
      getFromFile("/Users/astorm/voice/static/recorder.js")
    } ~
    path("style.css") {
      getFromFile("/Users/astorm/voice/static/style.css")
    } ~
    path("fonts" / "aller_lt-webfont.woff2") {
      getFromFile("/Users/astorm/voice/static/aller_lt-webfont.woff2")
    } ~
    path("fonts" / "aller_rg-webfont.woff2") {
      getFromFile("/Users/astorm/voice/static/aller_rg-webfont.woff2")
    }
}
