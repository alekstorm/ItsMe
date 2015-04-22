package api

import akka.actor.{Actor, ActorLogging}
import spray.http._
import spray.http.StatusCodes._
import spray.routing._
import spray.util.LoggingContext
import util.control.NonFatal
import spray.httpx.marshalling.Marshaller

case class ErrorResponseException(responseStatus: StatusCode, response: Option[HttpEntity]) extends Exception

class RoutedHttpService(route: Route) extends Actor with HttpService with ActorLogging {

  implicit def actorRefFactory = context

  implicit val handler = ExceptionHandler {
    case NonFatal(ErrorResponseException(statusCode, entity)) => ctx =>
      ctx.complete((statusCode, entity))

    case NonFatal(e) => ctx => {
      log.error(e, InternalServerError.defaultMessage)
      ctx.complete(InternalServerError)
    }
  }

  def receive: Receive =
    runRoute(route)(handler, RejectionHandler.Default, context, RoutingSettings.default, LoggingContext.fromActorRefFactory)
}
