package api

import java.util.UUID

import spray.json._
import spray.httpx.SprayJsonSupport
import spray.httpx.marshalling.MetaMarshallers

trait DefaultJsonFormats extends DefaultJsonProtocol with SprayJsonSupport with MetaMarshallers {
  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(x: UUID) = JsString(x.toString)
    def read(value: JsValue) = value match {
      case JsString(x) => UUID.fromString(x)
      case x           => deserializationError("Expected UUID as JsString, but got " + x)
    }
  }
}
