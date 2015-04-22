import sbt._
import Keys._
import spray.revolver.RevolverPlugin.Revolver

name := "brightroll-interview"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers += "spray nightlies" at "http://nightlies.spray.io"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies ++= {
  val akkaVersion  = "2.3.2"
  val sprayVersion = "1.3.1"
  Seq(
    "com.github.tminglei" %% "slick-pg" % "0.8.0",
    "com.typesafe.akka" %% "akka-actor" % akkaVersion
      exclude ("org.scala-lang" , "scala-library"),
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
      exclude ("org.slf4j", "slf4j-api")
     exclude ("org.scala-lang" , "scala-library"),
    "com.typesafe.slick" %% "slick" % "2.1.0",
    "edu.cmu.sphinx" % "sphinx4-core" % "1.0-SNAPSHOT",
    "edu.cmu.sphinx" % "sphinx4-data" % "1.0-SNAPSHOT",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "io.spray" % "spray-can" % sprayVersion,
    "io.spray" % "spray-client" % sprayVersion,
    "io.spray" % "spray-routing" % sprayVersion,
    "io.spray" %% "spray-json" % "1.2.5" exclude ("org.scala-lang" , "scala-library"),
    "org.specs2" %% "specs2" % "1.14" % "test",
    //"org.java-websocket" % "Java-WebSocket" % "1.3.1",
    "io.spray" % "spray-testkit" % sprayVersion % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "org.scalatest"       %   "scalatest_2.10" % "2.0" % "test"
  )
}

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

testOptions += Tests.Argument(TestFrameworks.JUnit, "-v")

unmanagedResourceDirectories in Compile <++= baseDirectory {
  base => Seq(base / "src/main/angular")
}

Revolver.settings : Seq[sbt.Def.Setting[_]]

crossPaths := false

//conflictManager := ConflictManager.loose

net.virtualvoid.sbt.graph.Plugin.graphSettings
