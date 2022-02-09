package btc.logger

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityContext, EntityTypeKey }
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria }

import java.util.Date
import scala.concurrent.duration.DurationInt

object BTCLogger {

  final case class State(amount: Double) extends CborSerializable {

    def update(amount: Double): State = State(this.amount + amount)

  }
  object State {
    val empty: State = State(amount = 1000)
  }

  /**
   * This interface defines all the commands (messages) that the BTCLogger actor supports.
   */
  sealed trait Command extends CborSerializable

  /**
   * A command to insert BTC log
   *
   * It replies with `StatusReply[Summary]`, which is sent back to the caller when
   * all the events emitted by this command are successfully persisted.
   */
  final case class InsertBTC(amount: Double, dateTime: Date, replyTo: ActorRef[StatusReply[State]]) extends Command

  /**
   * This interface defines all the events that the BTCLogger supports.
   */
  sealed trait Event extends CborSerializable

  final case class BTCInserted(total: Double, dateTime: Date, delta: Double) extends Event

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("BTCLogger")

  val tags: Vector[String] = Vector.tabulate(5)(i => s"logs-$i")
  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = { entityContext =>
      val i = math.abs(entityContext.entityId.hashCode % tags.size)
      val selectedTag = tags(i)
      BTCLogger(entityContext.entityId, selectedTag)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(entityId: String, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, entityId),
        emptyState = State.empty,
        commandHandler = (state, command) => handleCommand(state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def handleCommand(state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case InsertBTC(amount, dateTime, replyTo) =>
        if (amount <= 0) {
          Effect.reply(replyTo)(StatusReply.Error("Amount must be greater than zero"))
        } else {
          Effect.persist(BTCInserted(state.amount, dateTime, amount)).thenReply(replyTo)(StatusReply.Success(_))
        }
    }

  private def handleEvent(state: State, event: Event): State = {
    event match {
      case BTCInserted(_, _, amount) =>
        state.update(amount)
    }
  }

}
