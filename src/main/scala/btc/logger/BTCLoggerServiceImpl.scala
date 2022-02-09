package btc.logger

import akka.actor.typed.{ ActorSystem, DispatcherSelector }
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import btc.logger.proto._
import btc.logger.repository.{ BTCHourlyLogRepository, ScalikeJdbcSession }
import io.grpc.Status
import org.slf4j.LoggerFactory

import java.text.SimpleDateFormat
import java.util.concurrent.TimeoutException
import java.util.{ Date, UUID }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

class BTCLoggerServiceImpl(system: ActorSystem[_], btcHourlyLogRepository: BTCHourlyLogRepository)
    extends proto.BTCLoggerService {
  private val logger = LoggerFactory.getLogger(getClass)

  private val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"
  private val dateFormat = new SimpleDateFormat(DATE_FORMAT)

  import system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(system.settings.config.getDuration("btc-logger-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(DispatcherSelector.fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher"))

  val staticEntityId: String = UUID.randomUUID().toString

  override def insertBTC(in: InsertBTCRequest): Future[SuccessResponse] = {
    val entityRef = sharding.entityRefFor(BTCLogger.EntityKey, staticEntityId)
    val reply = for {
      date <- Future.fromTry(convertStringToDateTime(in.datetime))
      _ <- entityRef.askWithStatus(BTCLogger.InsertBTC(in.amount, date, _))
    } yield SuccessResponse(success = true)
    convertError(reply)
  }

  override def getHourlyLog(in: GetLogRequest): Future[GetLogResponse] = {

    for {
      startTime <- Future.fromTry(convertStringToDateTime(in.startDatetime))
      endTime <- Future.fromTry(convertStringToDateTime(in.endDatetime))
      log <- Future {
        ScalikeJdbcSession.withSession(btcHourlyLogRepository.getLogs(_, startTime, endTime))
      }(blockingJdbcExecutor)
    } yield {
      GetLogResponse(log.map { case (hour, amount) =>
        // TODO: save hour in millseconds and avoid multiplication by 1000
        HourlyLogResponse(new Date(hour * 1000).toInstant.toString, amount)
      })
    }
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(new GrpcServiceException(Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }

  private def convertStringToDateTime(dateString: String): Try[Date] =
    Try(dateFormat.parse(dateString)).recoverWith { case _: java.text.ParseException =>
      throw new GrpcServiceException(Status.FAILED_PRECONDITION.withDescription("Received unrecognizable timestamp"))
    }

}
