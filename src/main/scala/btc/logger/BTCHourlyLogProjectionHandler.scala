package btc.logger

import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import btc.logger.repository.{ BTCHourlyLogRepository, ScalikeJdbcSession }
import org.slf4j.LoggerFactory

class BTCHourlyLogProjectionHandler(tag: String, system: ActorSystem[_], repo: BTCHourlyLogRepository)
    extends JdbcHandler[EventEnvelope[BTCLogger.Event], ScalikeJdbcSession]() {

  private val log = LoggerFactory.getLogger(getClass)

  override def process(session: ScalikeJdbcSession, envelope: EventEnvelope[BTCLogger.Event]): Unit = {
    envelope.event match {
      case BTCLogger.BTCInserted(total, dateTime, delta) =>
        repo.update(session, total, delta, dateTime)
        log.info("BTCHourlyLogProjectionHandler({}) btc inserted for '{}': [{}]", tag, delta, dateTime)
    }
  }

}
