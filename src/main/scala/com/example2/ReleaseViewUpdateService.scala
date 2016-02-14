package com.example2

import java.sql.Date

import com.example2.ReleaseStatus.ReleaseStatus
import com.typesafe.config.Config
import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.messaging.event.OfficeEventMessage
import pl.newicom.dddd.view.sql.Projection.ProjectionAction
import pl.newicom.dddd.view.sql.{Projection, SqlViewStoreConfiguration, SqlViewUpdateConfig, SqlViewUpdateService}
import slick.dbio.Effect.Write
import slick.dbio._
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable._

import scala.concurrent.ExecutionContext

class ReleaseViewUpdateService(override val config: Config)(override implicit val profile: JdbcProfile)
  extends SqlViewUpdateService with ReleaseReadConfiguration {

  lazy val releaseDao = new ReleaseDao

  override def vuConfigs: Seq[SqlViewUpdateConfig] = {
    List(
      SqlViewUpdateConfig("releases", ReleaseOfficeId, new ReleaseProjection(releaseDao))
    )
  }

  override def onViewUpdateInit: DBIO[Unit] = {
    super.onViewUpdateInit >>
      releaseDao.ensureSchemaCreated
  }
}

trait ReleaseReadConfiguration extends SqlViewStoreConfiguration

case class ReleaseView(id: EntityId, status: ReleaseStatus, createDate: Date, componentName: String,
                       version: String, gitCommit: Option[String], gitTag: Option[String],
                       deploymentStarted: Option[Long], deploymentEnded: Option[Long], deploymentLength: Option[Long],
                       deploymentStatus: Option[String])

class ReleaseDao(implicit val profile: JdbcProfile, ec: ExecutionContext)  {
  import profile.api._

  implicit val reservationStatusColumnType = MappedColumnType.base[ReleaseStatus, String](
    { c => c.toString },
    { s => ReleaseStatus.withName(s)}
  )

  val ReleasesTableName = "releases"

  class Releases(tag: Tag) extends Table[ReleaseView](tag, ReleasesTableName) {
    def id = column[EntityId]("ID", O.PrimaryKey)
    def status = column[ReleaseStatus]("STATUS")
    def createDate = column[Date]("CREATE_DATE")

    def componentName = column[String]("COMPONENT_NAME")
    def version = column[String]("VERSION")
    def gitCommit = column[Option[String]]("GIT_COMMIT")
    def gitTag = column[Option[String]]("GIT_TAG")

    def deploymentStarted = column[Option[Long]]("DEPLOYMENT_STARTED")
    def deploymentEnded = column[Option[Long]]("DEPLOYMENT_ENDED")
    def deploymentLength = column[Option[Long]]("DEPLOYMENT_LENGTH")
    def deploymentStatus = column[Option[String]]("DEPLOYMENT_STATUS")

    def * = (id, status, createDate, componentName, version, gitCommit, gitTag,
      deploymentStarted, deploymentEnded, deploymentLength, deploymentStatus) <> (ReleaseView.tupled, ReleaseView.unapply)
  }

  val releases = TableQuery[Releases]

  /**
    * Queries impl
    */
  private val by_id = releases.findBy(_.id)


  /**
    * Public interface
    */

  /*
    def createIfNotExists(view: ReleaseView)(implicit s: Session): ReleaseView = {
      by_id(view.id).run.headOption.orElse {
        releases.insert(view)
        Some(view)
      }.get
    }
  */

  def createOrUpdate(view: ReleaseView) =
    releases.insertOrUpdate(view)

  def updateStatus(viewId: EntityId, status: ReleaseStatus.Value) =
    releases.filter(_.id === viewId).map(_.status).update(status)

  def updateDeployment(viewId: EntityId, deployment: Deployment, status: ReleaseStatus.Value) = {
    val query = for {
      r <- releases if r.id === viewId
    } yield (r.deploymentStarted, r.deploymentEnded, r.deploymentLength, r.deploymentStatus, r.status)
    query.update(Some(deployment.started), deployment.ended, deployment.length, Some(deployment.status), status)
  }

  def all =  releases.result

  def byId(id: EntityId) = by_id(id).result.headOption

  def remove(id: EntityId) = by_id(id).delete

  def ensureSchemaDropped =
    getTables(ReleasesTableName).headOption.flatMap {
      case Some(table) => releases.schema.drop.map(_ => ())
      case None => DBIO.successful(())
    }

  def ensureSchemaCreated =
    getTables(ReleasesTableName).headOption.flatMap {
      case Some(table) => DBIO.successful(())
      case None => releases.schema.create.map(_ => ())
    }
}

class ReleaseProjection(dao: ReleaseDao)(implicit ec: ExecutionContext) extends Projection {
  override def consume(eventMessage: OfficeEventMessage): ProjectionAction[Write] = eventMessage.event match {
    case ReleaseCreated(id, info) =>
      // TODO: "new Date" here can't be right — it's what leaven seems to do, though
      val newView = ReleaseView(id, ReleaseStatus.NotDeploying, new Date(DateTime.now().getMillis), info.componentName, info.version, info.gitCommit, info.gitTag, None, None, None, None)
      dao.createOrUpdate(newView)
    case DeploymentStarted(id, deployment) =>
      dao.updateDeployment(id, deployment, ReleaseStatus.Deploying)
    case DeploymentEnded(id, deployment) =>
      dao.updateDeployment(id, deployment, ReleaseStatus.NotDeploying)
  }
}
