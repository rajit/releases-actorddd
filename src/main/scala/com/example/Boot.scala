package com.example

import java.util.UUID

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.example.ReleaseProtocol.{CreateRelease, ReleaseInfo}
import com.example.ReleasesProtocol.{GetReleases, ReleasesDto}
import com.typesafe.config.ConfigFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object Boot extends App {

  val config = ConfigFactory.load()

  implicit val system = ActorSystem("DDDSystem", config)

  implicit val timeout = Timeout(2 seconds)

  // Initialise the domain model with the Release aggregate root and the single query model
  val domainModel = new DomainModel(system)
    .register(Release)
    .registerQueryModel(Releases)

  val releaseId = UUID.randomUUID()
  val releaseOne = domainModel.aggregateRootOf(Release, releaseId)

  releaseOne ! CreateRelease(ReleaseInfo("component1", "1.2", None, None))

  Thread.sleep(2000)

  val releases = domainModel.queryModelOf(Releases)

  (releases ? GetReleases).map {
    case ReleasesDto(r, t) => r.foreach(println)
  }
}
