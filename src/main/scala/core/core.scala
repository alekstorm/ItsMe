package core

import java.nio.{ByteBuffer, ByteOrder}
import java.nio.file.{Files, NoSuchFileException, Paths, StandardOpenOption}
import java.util.EnumSet

import scala.math

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

import api.{Api, RoutedHttpService, VoiceActor}

trait Core {
  protected implicit def system: ActorSystem
}

object BootedCore extends App with Core with Api {
  def system: ActorSystem = ActorSystem("voice")
  def actorRefFactory = system

  val rootService = system.actorOf(Props(classOf[RoutedHttpService], routes))
  val uploadService = system.actorOf(Props[VoiceActor])

  private val rs = new WebSocketServer(9000)
  rs.handleResource("/login", Some(uploadService))
  rs.handleResource("/register", Some(uploadService))
  rs.start()

  IO(Http)(system) ! Http.Bind(rootService, "0.0.0.0", port = 8000)

  sys.addShutdownHook {
    rs.stop()
    system.shutdown()
  }
}
