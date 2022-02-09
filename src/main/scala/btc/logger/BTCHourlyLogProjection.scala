package btc.logger

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ ExactlyOnceProjection, SourceProvider }
import akka.projection.{ ProjectionBehavior, ProjectionId }
import btc.logger.repository.{ BTCHourlyLogRepository, ScalikeJdbcSession }

object BTCHourlyLogProjection {

  def init(system: ActorSystem[_], repository: BTCHourlyLogRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "BTCHourlyLogProjection",
      BTCLogger.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      repository: BTCHourlyLogRepository,
      index: Int)
      : ExactlyOnceProjection[Offset, EventEnvelope[BTCLogger.Event]] = {
    val tag = BTCLogger.tags(index)

    val sourceProvider: SourceProvider[Offset, EventEnvelope[BTCLogger.Event]] =
      EventSourcedProvider.eventsByTag[BTCLogger.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("BTCHourlyLogProjection", tag),
      sourceProvider,
      handler =
        () => new BTCHourlyLogProjectionHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}
