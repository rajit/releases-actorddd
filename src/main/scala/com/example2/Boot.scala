package com.example2

import java.net.InetAddress

import akka.actor._
import akka.japi.Util.immutableSeq
import akka.cluster.Cluster
import akka.util.Timeout
import scala.concurrent.duration._
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.Logger
import org.slf4j.LoggerFactory._
import pl.newicom.dddd.actor.{CreationSupport, PassivationConfig}
import pl.newicom.dddd.aggregate.AggregateRootActorFactory
import pl.newicom.dddd.cluster._
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.OfficeEventMessage
import pl.newicom.dddd.monitoring.AggregateRootMonitoring
import pl.newicom.dddd.office.{Office, OfficeFactory}
import pl.newicom.dddd.persistence.PersistentActorLogging
import pl.newicom.dddd.writefront.CommandHandler
import slick.driver.{JdbcProfile, PostgresDriver}

import scala.io.Source
import scala.util.Try

object Boot extends App with ReleaseBackendConfiguration {
  lazy val log = getLogger(this.getClass.getName)

  lazy val config = ConfigFactory.load()
  implicit lazy val system = ActorSystem("DDDSystem", config)

  val seedList = seeds(config)
  log.info(s"Joining cluster with seed nodes: $seedList")
  Cluster(system).joinSeedNodes(seedList.toSeq)

  val releaseOffice = OfficeFactory.office[Release]

  implicit val profile: JdbcProfile = PostgresDriver
  system.actorOf(Props(new ReleaseViewUpdateService(config)), "release-view-update-service")

  addShutdownHook()

  Thread.sleep(2000)

  val cmdHandler = system.actorOf(Props(classOf[CommandHandlerActor]))
  cmdHandler ! CreateRelease("1234", ReleaseInfo("Bulky", "v1", Some("asdf"), Some("v1")))

  private def addShutdownHook(): Unit = {
    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run(): Unit = {
        log.info("")
        log.info("Shutting down Akka...")

        system.terminate()

        log.info("Successfully shut down Akka")
      }
    }))
  }
}

class CommandHandlerActor extends Actor with CommandHandler {
  import context.dispatcher
  implicit val timeout = new Timeout(2.seconds)

  override def receive = {
    case cmd: Command => handle(ReleaseOfficeId, CommandMessage(cmd))
  }

  lazy val contactPoints: Seq[String] = Seq("akka.tcp://DDDSystem@192.168.1.57:2552]")
//  lazy val contactPoints: Seq[String] = immutableSeq(context.system.settings.config.getStringList("app.backend-contact-points"))
}

trait LocalPublisher extends EventPublisher {
  this: Actor with PersistentActorLogging =>

  override def publish(em: OfficeEventMessage): Unit = {
    context.system.eventStream.publish(em.event)
    log.debug(s"Published: $em")
  }
}

trait ReleaseBackendConfiguration {

  def log: Logger
  def config: Config
  implicit def system: ActorSystem
  def creationSupport = implicitly[CreationSupport]
  def releaseOffice: Office[Release]

  //
  // Reservation Office
  //
  implicit object ReleaseAggregateRootActorFactory extends AggregateRootActorFactory[Release] {

    override def props(pc: PassivationConfig) = Props(new Release(pc) with LocalPublisher with AggregateRootMonitoring) //{
//      override def toEventMessage(event: DomainEvent): EventMessage = {
//        event match {
//          case rc: ReleaseConfirmed =>
//            EventMessage(event)
//              .withMetaAttribute("commandTimestamp", commandTraceContext.startTimestamp.nanos)
//              .withMetaAttribute("commandName", "ConfirmRelease")
//          case _ =>
//            super.toEventMessage(event)
//        }
//      }
//    })
  }

  implicit object ReleaseShardResolution extends DefaultShardResolution[Release]

  def seeds(config: Config) = {
    // Read cluster seed nodes from the file specified in the configuration
    Try(config.getString("app.cluster.seedsFile")).toOption match {
      case Some(seedsFile) =>
        // Seed file was specified, read it
        log.info(s"reading seed nodes from file: $seedsFile")
        Source.fromFile(seedsFile).getLines().map { address =>
          AddressFromURIString.parse(s"akka.tcp://sales@$address")
        }.toList
      case None =>
        // No seed file specified, use this node as the first seed
        log.info("no seed file found, using default seeds")
        val port = config.getInt("app.port")
        val localAddress = Try(config.getString("app.host"))
          .toOption.getOrElse(InetAddress.getLocalHost.getHostAddress)
        List(AddressFromURIString.parse(s"akka.tcp://release@$localAddress:$port"))
    }
  }

}
