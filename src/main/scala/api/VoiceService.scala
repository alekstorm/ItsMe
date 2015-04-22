package api

import java.io.{BufferedReader, FileReader, PipedInputStream, PipedOutputStream}
import java.net.URLDecoder
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.channels.WritableByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, NoSuchFileException, Paths, StandardOpenOption}
import java.util.{EnumSet, UUID}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.sys.process._
import scala.util.Random
import scala.util.control.Exception._

import akka.actor.{Actor, ActorRef, Props}
import edu.cmu.sphinx.api.{AbstractSpeechRecognizer, Configuration, SpeechResult}
import edu.cmu.sphinx.frontend.util.StreamDataSource
import org.java_websocket.WebSocket
import spray.json._

import core.WebSocketServer
import models._
import models.Driver.simple._

class VoiceActor extends Actor {
  val clients = mutable.Map.empty[WebSocket, ActorRef]

  def receive = {
    case WebSocketServer.Open(ws, handshake) =>
      val Array(path, query) = handshake.getResourceDescriptor.split("\\?")
      val params = Map(query.split("&").map { pair =>
        val pieces = pair.split("=")
        (pieces(0), pieces(1))
      }: _*).mapValues(v => URLDecoder.decode(v, StandardCharsets.UTF_8.toString))

      val strategy = params("strategy")
      val `class` = path match {
        case "/login" =>
          strategy match {
            case "challenge" => classOf[ChallengeLoginActor]
            //case "pin" => classOf[PinLoginActor]
          }
        //case "/register" => classOf[RegisterActor]
      }
      clients(ws) = context.system.actorOf(Props(`class`, strategy, params, (output: JsObject) => ws.send(output.compactPrint)))

      /*val (request, wordCount) = path match {
        // TODO check password valid before continuing
        case "/login" => (Login(params("email"), params("password")), loginWords)
        //case "/register" => (Register(params("name"), params("email"), params("password"), params("gender")), registerWords)
      }

      strategy match {
        case "challenge" =>
          clients(ws) = (request, sendNext(ws, ChallengeState(sink, out, null, wordCount)))
        case "pin" =>
          val code = (0 until 4).map(_ => Random.nextInt(10))
          val pin = db.withSession { implicit s =>
            Users.getByEmail(request.email).get.pin
          }.map(_.toString.toInt).zip(code).map(p => (p._1 + p._2) % 10)
          ws.send(JsObject("name" -> JsString("start"), "params" -> JsObject("code" -> JsArray(code.map(JsNumber.apply): _*))).compactPrint)
          clients(ws) = (request, PinState(sink, pin.map { case 0 => "zero"; case 1 => "one"; case 2 => "two"; case 3 => "three"; case 4 => "four"; case 5 => "five"; case 6 => "six"; case 7 => "seven"; case 8 => "eight"; case 9 => "nine"; case 10 => "ten"; case 11 => "eleven"; case 12 => "twelve"; case 13 => "thirteen"; case 14 => "fourteen"; case 15 => "fifteen"; case 16 => "sixteen"; case 17 => "seventeen"; case 18 => "eighteen" }.toList))
      }*/

    case WebSocketServer.BinaryMessage(ws, samples) =>
      clients.get(ws).foreach(_ ! LoginActor.Message(samples))

    case WebSocketServer.Close(ws, _, _, _) =>
      clients.get(ws).foreach { ref =>
        clients -= ws
        ref ! LoginActor.Close
      }

    /*case Result(ws, result) =>
      // TODO does findToken look through the *whole* lattice?
      // TODO score against target pronunciation, rather than looking for word in N-best
      // TODO apply transform for target speaker to improve recognition - see sphinx4 diarization example
      val (request, state) = clients(ws) // TODO handle non-existence
      state match {
        case state @ ChallengeState(out, sink, word, remaining) =>
          }
        case PinState(sink, words) =>
          // TODO http://cmusphinx.sourceforge.net/wiki/sphinx4:rejectionhandling
          println(result.getWords.asScala.filter(!_.isFiller).map(_.getWord.getSpelling))
          if (result.getWords.asScala.filter(!_.isFiller).map(_.getWord.getSpelling).containsSlice(words))
            ws.send(JsObject("name" -> JsString("success")).compactPrint)
          // just time out instead of failing
      }*/
  }
}

object LoginActor {
  case class Start(params: Map[String, String])
  case class Message(samples: ByteBuffer)
  case class Result(result: SpeechResult)
  case object Close

  lazy val db = Database.forURL("jdbc:postgresql://localhost/postgres", driver="org.postgresql.Driver")

  def getConfiguration(model: String) = {
    val configuration = new Configuration()
    configuration.setAcousticModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us")
    configuration.setDictionaryPath("resource:/edu/cmu/sphinx/models/en-us/cmudict-en-us.dict")
    // TODO use SimpleNGramModel instead of TrigramModel? - actually, use a fixed grammar instead - just single nouns
    configuration.setLanguageModelPath(s"file:$model-model.dmp")
    configuration
  }
}

abstract class LoginActor(model: String) extends Actor {
  import LoginActor._

  var out: WritableByteChannel = _
  var sink: PipedOutputStream = _

  val fileID = UUID.randomUUID()
  val rawPath = Paths.get(fileID + ".raw")
  val wavPath = Paths.get(fileID + ".wav")
  out = Files.newByteChannel(rawPath, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE))

  sink = new PipedOutputStream
  val source = new PipedInputStream(sink) // have to ensure the sink is connected to something before context-switching

  // TODO move into separate function to prevent modifying variables in closure
  // TODO dealing with out-of-grammar utterances will also help - http://cmusphinx.sourceforge.net/wiki/sphinx4:rejectionhandling
  Future {
    val speechRecognizer = new AbstractSpeechRecognizer(getConfiguration(model)) {
      context.getInstance(classOf[StreamDataSource]).setInputStream(source)
      recognizer.allocate()

      def stop() {
        recognizer.deallocate()
      }
    }

    // TODO check thread interrupted flag
    var result = speechRecognizer.getResult()
    while (result != null) {
      self ! Result(result)
      result = speechRecognizer.getResult()
    }
    speechRecognizer.stop()
  }

  def close() {
    out.close()
    val copyOut = Files.newByteChannel(wavPath, EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE))

    val samples = Files.readAllBytes(rawPath)
    val sampleRate = 16000

    val header = ByteBuffer.allocateDirect(44)
    header.order(ByteOrder.LITTLE_ENDIAN)

    def putString(s: String) {
      s.getBytes.foreach(header.put)
    }

    // TODO all putInt's should be unsigned; watch overflow
    putString("RIFF") // RIFF identifier
    header.putInt(36 + samples.length) // RIFF chunk length
    putString("WAVE") // RIFF type
    putString("fmt ") // format chunk identifier
    header.putInt(16) // format chunk length
    header.putShort(1) // sample format (raw)
    header.putShort(1) // channel count
    header.putInt(sampleRate) // sample rate
    header.putInt(sampleRate * 4) // byte rate (sample rate * block align)
    header.putShort(2) // block align (channel count * bytes per sample)
    header.putShort(16) // bits per sample
    putString("data") // data chunk identifier
    header.putInt(samples.length) // data chunk length

    header.position(0)
    copyOut.write(header)
    copyOut.write(ByteBuffer.wrap(samples))
    copyOut.close()
  }

  def receive = {
    case Message(samples) =>
      out.write(samples)
      sink.write(samples.array, 0, samples.array.size)

    case Close =>
      out.close()
  }
}

object ChallengeLoginActor {
  lazy val words = Files.readAllLines(Paths.get("challenge-model"), StandardCharsets.UTF_8).asScala.toSeq
}

class ChallengeLoginActor(strategy: String, params: Map[String, String], send: JsValue => Unit) extends LoginActor(strategy) {
  import LoginActor._
  import ChallengeLoginActor._

  var word: String = _
  var remaining = 15

  def sendNext() {
    word = words(Random.nextInt(words.size))
    remaining = remaining - 1
    send(JsObject("name" -> JsString("next"), "params" -> JsObject("word" -> JsString(word), "remaining" -> JsNumber(remaining))))
  }

  sendNext()

  override def receive = ({
    case Result(result) =>
      if (result.getResult.findToken("skip") != null) {
        word = words(Random.nextInt(words.size))
        send(JsObject("name" -> JsString("next"), "params" -> JsObject("word" -> JsString(word), "remaining" -> JsNumber(remaining))))
      }
      // FIXME try searching n-best instead
      else if (result.getResult.findToken(word) != null) {
        if (remaining == 0) {
          send(JsObject("name" -> JsString("recognizing")))
          close()

          Future {
            def mangleID(id: UUID) = id.toString.replace("-", "")

            // TODO remember to force recognizer to use the correct gender; don't auto-detect
            //request match {
              //case Login(email, password) =>
                // TODO calling this twice is necessary to prevent errors; should fix
                val fileID = "a617027e-1435-439d-a047-dd72b3a3d22a" // TODO remove
                fr.lium.spkDiarization.programs.MSegInit.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sOutputMask=%s.i.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MDecode.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,0:0:0", "--sInputMask=%s.i.seg", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/sms.gmms", "--dPenality=10,10,50", "--sOutputMask=%s.pms.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MSeg.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.i.seg", "--kind=FULL", "--sMethod=GLR", "--sOutputMask=%s.s.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MClust.main(Array("--fInputMask=%s.wav", "--fInputSpeechThr=0.1", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.s.seg", "--cMethod=l", "--cThr=2", "--sOutputMask=%s.l.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MClust.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.l.seg", "--cMethod=h", "--cThr=3", "--sOutputMask=%s.h.3.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MTrainInit.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.h.3.seg", "--nbComp=8", "--kind=DIAG", "--tOutputMask=%s.init.gmms", fileID.toString))
                fr.lium.spkDiarization.programs.MTrainEM.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.h.3.seg", "--tInputMask=%s.init.gmms", "--nbComp=8", "--kind=DIAG", "--tOutputMask=%s.gmms", fileID.toString))
                fr.lium.spkDiarization.programs.MDecode.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.h.3.seg", "--tInputMask=%s.gmms", "--dPenality=250", "--sOutputMask=%s.d.3.seg", fileID.toString))
                fr.lium.spkDiarization.tools.SAdjSeg.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=%s.d.3.seg", "--sOutputMask=%s.adj.3.seg", fileID.toString))
                fr.lium.spkDiarization.tools.SFilter.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,0:0:0", "--sInputMask=%s.adj.3.seg", "--fltSegMinLenSpeech=150", "--fltSegMinLenSil=25", "--sFilterClusterName=j", "--fltSegPadding=25", "--sFilterMask=%s.pms.seg", "--sOutputMask=%s.flt.3.seg", fileID.toString))
                fr.lium.spkDiarization.tools.SSplitSeg.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,0:0:0", "--sInputMask=%s.flt.3.seg", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/s.gmms", "--sFilterMask=%s.pms.seg", "--sFilterClusterName=iS,iT,j", "--sOutputMask=%s.spl.3.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MScore.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,1:1:300:4", "--sInputMask=%s.spl.3.seg", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/gender.gmms", "--sGender", "--sByCluster", "--sOutputMask=%s.g.3.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MClust.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,1:1:300:4", "--sInputMask=%s.g.3.seg", "–fInputSpeechThr=1", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/ubm.gmm", "--cMethod=ce", "--cThr=1.5", "--emCtrl=1,5,0.01", "--sTop=5,/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/ubm.gmm", "--tOutputMask=%s.c.gmm", "--sOutputMask=%s.seg", fileID.toString))

                // TODO rest of commands

                fr.lium.spkDiarization.programs.MSegInit.main(Array("--fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:1:0:0:0:0,13,0:0:0", "--sInputMask=", "--sOutputMask=%s.s.seg", fileID.toString))
                fr.lium.spkDiarization.programs.MDecode.main(Array(" --fInputMask=%s.wav", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,0:0:0", "--sInputMask=%s.s.seg", "--sOutputMask=%s.g.seg", "--dPenality=10,10,50", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/sms.gmms", fileID.toString))
                fr.lium.spkDiarization.programs.MScore.main(Array("--help", "--sGender", "--sByCluster", "--fInputDesc=audio2sphinx,1:3:2:0:0:0,13,1:1:0:0", "--fInputMask=%s.wav", "--sInputMask=%s.g.seg", "--sOutputMask=%s.seg", "--tInputMask=/System/Library/Frameworks/Python.framework/Versions/2.7/share/voiceid/gender.gmms", fileID.toString))

                // TODO rest of commands

                val Some(user) = db.withSession { implicit s =>
                  Users.getByLogin(params("email"), params("password"))
                }
                val userID = mangleID(user.id)
                // FIXME weighted average (by length) over all clusters
                var reader: BufferedReader = null
                var line: String = null
                try {
                  reader = new BufferedReader(new FileReader(s"$fileID/S0.ident.${user.gender.toUpperCase}.$userID.gmm.seg"))
                  line = reader.readLine()
                }
                finally {
                  if (reader != null)
                    reader.close()
                }
                val ubmScore = """score:UBM = (-\d+\.\d+)""".r.findFirstMatchIn(line).get.group(1).toDouble
                val userScore = ("score:" + userID + """ = (-\d+\.\d+)""").r.findFirstMatchIn(line).get.group(1).toDouble
                val success = userScore - ubmScore >= 0.65
                send(JsObject("name" -> JsString("result"), "params" -> JsObject("success" -> JsBoolean(success))))
              /*case Register(name, email, password, gender, pin) =>
                val id = mangleID(db.withSession { implicit s =>
                  Users.create(name, email, password, gender, pin)
                })
                s"/Users/astorm/voiceid-0.3/scripts/vid -s $id -g $fileID.wav".!!
                val malePath = Paths.get(s"/Users/astorm/.voiceid/gmm_db/M/$id.gmm")
                val femalePath = Paths.get(s"/Users/astorm/.voiceid/gmm_db/F/$id.gmm")
                ignoring(classOf[NoSuchFileException]) {
                  Files.copy(malePath, femalePath)
                  Files.copy(femalePath, malePath)
                }*/
            //}
          }
        }
        else
          sendNext()
      }
  }: Receive) orElse super.receive
}
