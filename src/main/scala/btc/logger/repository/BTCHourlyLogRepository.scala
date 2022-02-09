package btc.logger.repository

import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Date

trait BTCHourlyLogRepository {
  def update(session: ScalikeJdbcSession, total: Double, amount: Double, dateTime: Date): Unit
  def getLogs(session: ScalikeJdbcSession, start: Date, end: Date): List[(Long, Double)]
}

class BTCHourlyLogRepositoryImpl extends BTCHourlyLogRepository {
  private val tableName = "btc_hourly_log"
  override def update(session: ScalikeJdbcSession, total: Double, delta: Double, dateTime: Date): Unit = {
    session.db.withinTx { implicit dbSession =>
      val curTotal = total + delta
      val hourForDateTime = dateTime.toInstant.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1)).getEpochSecond
      val query = s"""INSERT INTO btc_hourly_log (amount, hour) VALUES ($curTotal, $hourForDateTime)
                   |               ON CONFLICT (hour) DO UPDATE SET amount = btc_hourly_log.amount + $delta""".stripMargin
      println(query)
      sql"""INSERT INTO btc_hourly_log (amount, hour) VALUES ($curTotal, $hourForDateTime)
               ON CONFLICT (hour) DO UPDATE SET amount = btc_hourly_log.amount + $delta""".executeUpdate().apply()
    }
  }

  override def getLogs(session: ScalikeJdbcSession, start: Date, end: Date): List[(Long, Double)] = {
    session.db.readOnly { implicit dbSession =>
      val startHour = start.toInstant.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1)).getEpochSecond
      val endHour = end.toInstant.truncatedTo(ChronoUnit.HOURS).getEpochSecond
      println(startHour, endHour)
      sql"""SELECT amount, hour FROM btc_hourly_log WHERE hour BETWEEN $startHour AND $endHour"""
        .map { rs =>
          println(rs.any(1))
          (rs.long("hour"), rs.double("amount"))
        }
        .list()
        .apply()
    }
  }
}
