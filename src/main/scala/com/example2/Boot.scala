package com.example2

import java.net.InetAddress
import java.util.UUID

import akka.actor._
import akka.cluster.Cluster
import akka.util.Timeout
import com.example.ReleaseProtocol.{CreateRelease, ReleaseInfo}
import com.example.ReleasesProtocol.{GetReleases, ReleasesDto}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory._
import pl.newicom.dddd.office.OfficeFactory

import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

object Boot extends App {
  lazy val log = getLogger(this.getClass.getName)

  lazy val config = ConfigFactory.load()
  implicit lazy val system = ActorSystem("DDDSystem", config)

  val seedList = seeds(config)
  log.info(s"Joining cluster with seed nodes: $seedList")
  Cluster(system).joinSeedNodes(seedList.toSeq)

  val releaseOffice = OfficeFactory.office[Release]

  addShutdownHook()

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

  private def seeds(config: Config) = {
    // TODO: Make work correctly — copied directly from akka-leaven-ddd example

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
        List(AddressFromURIString.parse(s"akka.tcp://sales@$localAddress:$port"))
    }
  }
}
