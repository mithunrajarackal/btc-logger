package btc.logger

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import btc.logger.repository.{ BTCHourlyLogRepositoryImpl, ScalikeJdbcSetup }
import org.slf4j.{ Logger, LoggerFactory }

import scala.util.control.NonFatal

object Main {

  val logger: Logger = LoggerFactory.getLogger("btc.logger.Main")

  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Behaviors.empty, "BTCLoggerService")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {

    ScalikeJdbcSetup.init(system)
    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    BTCLogger.init(system)

    val btcHourlyLogRepository = new BTCHourlyLogRepositoryImpl
    BTCHourlyLogProjection.init(system, btcHourlyLogRepository)

    val grpcInterface =
      system.settings.config.getString("btc-logger-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("btc-logger-service.grpc.port")
    val grpcService = new BTCLoggerServiceImpl(system, btcHourlyLogRepository)
    BTCLoggerServer.start(grpcInterface, grpcPort, system, grpcService)
  }

}
