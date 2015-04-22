package core

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import scala.collection.mutable

import akka.actor.ActorRef
import org.java_websocket.WebSocket
import org.java_websocket.framing.CloseFrame
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.{WebSocketServer => BaseWebSocketServer}

object WebSocketServer {
  sealed trait WebSocketServerEvent
  case class Message(ws: WebSocket, msg: String) extends WebSocketServerEvent
  case class BinaryMessage(ws: WebSocket, msg: ByteBuffer) extends WebSocketServerEvent
  case class Open(ws: WebSocket, hs: ClientHandshake) extends WebSocketServerEvent
  case class Close(ws: WebSocket, code: Int, reason: String, external: Boolean) extends WebSocketServerEvent
  case class Error(ws: WebSocket, ex: Exception) extends WebSocketServerEvent
}

class WebSocketServer(port: Int) extends BaseWebSocketServer(new InetSocketAddress(port)) {
  import WebSocketServer._

  private val reactors = mutable.Map.empty[String, ActorRef]

  def handleResource(descriptor: String, reactor: Option[ActorRef]) = {
    if (descriptor != null) {
      reactor match {
        case Some(actor) => reactors += ((descriptor, actor))
        case None => reactors -= descriptor
      }
    }
  }

  private def handleEvent(ws: WebSocket, event: WebSocketServerEvent) = {
    if (ws != null) {
      reactors.get(ws.getResourceDescriptor.split("\\?")(0)) match {
        case Some(actor) => actor ! event
        case None => ws.close(CloseFrame.REFUSE)
      }
    }
  }

  override def onMessage(ws: WebSocket, msg: String) =
    handleEvent(ws, Message(ws, msg))

  override def onMessage(ws: WebSocket, msg: ByteBuffer) =
    handleEvent(ws, BinaryMessage(ws, msg))

  override def onOpen(ws: WebSocket, hs: ClientHandshake) =
    handleEvent(ws, Open(ws, hs))

  override def onClose(ws: WebSocket, code: Int, reason: String, external: Boolean) =
    handleEvent(ws, Close(ws, code, reason, external))
 
  override def onError(ws: WebSocket, ex: Exception) =
    handleEvent(ws, Error(ws, ex))
}
