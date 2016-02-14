package com.example2

import java.util.Date

import com.example2.Release.State
import com.example2.ReleaseStatus.ReleaseStatus
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate.{AggregateState, EntityId, AggregateRoot}
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.office.RemoteOfficeId
import pl.newicom.dddd.office.LocalOfficeId.fromRemoteId


object ReleaseStatus extends Enumeration {
  type ReleaseStatus = Value
  val NotDeploying, Deploying = Value
}

case class ReleaseDto(id: String, info: ReleaseInfo, currentDeployment: Option[Deployment], pastDeployments: Seq[Deployment])
case class ReleaseInfo(componentName: String, version: String, gitCommit: Option[String], gitTag: Option[String])
case class Deployment(started: Long, ended: Option[Long] = None, length: Option[Long] = None, status: String = "IN_PROGRESS")

case class ReleaseCreated(releaseId: EntityId, info: ReleaseInfo)
case class DeploymentStarted(releaseId: EntityId, deployment: Deployment)
case class DeploymentEnded(releaseId: EntityId, deployment: Deployment)

case class CreateRelease(id: EntityId, info: ReleaseInfo)
case class StartDeployment(id: EntityId)
case class EndDeployment(id: EntityId)

object Release {
  implicit object ReleaseOfficeId extends RemoteOfficeId("Release")
  implicit val officeId = fromRemoteId[Release](ReleaseOfficeId)

  case class State(status: ReleaseStatus, info: ReleaseInfo, deployment: Option[Deployment] = None, createDate: Date = new Date) extends AggregateState[State] {
    override def apply: StateMachine = {
      case DeploymentStarted(_, startedDeployment) =>
        copy(status = ReleaseStatus.Deploying, deployment = Some(startedDeployment))
      case DeploymentEnded(_, endedDeployment) =>
        copy(status = ReleaseStatus.NotDeploying, deployment = None)
    }
  }
}

abstract class Release(val pc: PassivationConfig) extends AggregateRoot[State, Release] {
  this: EventPublisher =>

  override val factory = {
    // TODO: Is "new Date" right here? Is the factory used in replays?
    case ReleaseCreated(id, info) =>
      Release.State(ReleaseStatus.NotDeploying, info)
  }

  // TODO: There is a lot of throwing here. Surely there's a better way (the style is copied from leaven project)
  override def handleCommand: Receive = {
    case CreateRelease(id, info) =>
      if (initialized) {
        throw new RuntimeException(s"Release $id already exists")
      } else {
        raise(ReleaseCreated(id, info))
      }

    case StartDeployment(id) =>
      if (state.status eq ReleaseStatus.Deploying) {
        throw new RuntimeException(s"Release $id is already deploying")
      } else {
        raise(DeploymentStarted(id, Deployment(started = System.currentTimeMillis())))
      }

    case EndDeployment(id) =>
      if (state.status eq ReleaseStatus.NotDeploying) {
        throw new RuntimeException(s"Release $id is not currently deploying")
      } else if (state.deployment.isEmpty) {
        throw new RuntimeException(s"Release $id does not current deployment")
      } else {
        val deployment = state.deployment.get
        val ended = System.currentTimeMillis
        raise(DeploymentEnded(id, deployment.copy(ended = Some(ended), length = Some(ended - deployment.started), status = "COMPLETED")))
      }
  }
}
