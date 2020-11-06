// Copyright © 2018-2020 Ultimate Software Group. <https://www.ultimatesoftware.com>

package com.ultimatesoftware.kafka.streams.javadsl

import java.util.Optional
import java.util.concurrent.CompletionStage

import akka.actor.ActorRef
import com.ultimatesoftware.kafka.streams.core
import com.ultimatesoftware.kafka.streams.core.{ AggregateRefTrait, GenericAggregateActor }

import scala.compat.java8.FutureConverters
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext

trait AggregateRef[Agg, Cmd, Event] {
  def getState: CompletionStage[Optional[Agg]]
  def ask(command: Cmd): CompletionStage[CommandResult[Agg]]
  def applyEvent(event: Event): CompletionStage[ApplyEventResult[Agg]]
}

final class AggregateRefImpl[Agg, Cmd, Event](
    val aggregateId: String,
    val region: ActorRef) extends AggregateRef[Agg, Cmd, Event]
  with AggregateRefTrait[Agg, Cmd, Event] {

  import DomainValidationError._
  private implicit val ec: ExecutionContext = ExecutionContext.global

  def getState: CompletionStage[Optional[Agg]] = {
    FutureConverters.toJava(queryState.map(_.asJava))
  }

  def ask(command: Cmd): CompletionStage[CommandResult[Agg]] = {
    val envelope = GenericAggregateActor.CommandEnvelope[Cmd](aggregateId, command)
    val result = askWithRetries(envelope).map {
      case Left(error: core.DomainValidationError) ⇒
        CommandFailure[Agg](error.asJava)
      case Left(error) ⇒
        CommandFailure[Agg](error)
      case Right(aggOpt) ⇒
        CommandSuccess[Agg](aggOpt.asJava)
    }
    FutureConverters.toJava(result)
  }

  def applyEvent(event: Event): CompletionStage[ApplyEventResult[Agg]] = {
    val envelope = GenericAggregateActor.ApplyEventEnvelope[Event](aggregateId, event)
    val result = applyEventsWithRetries(envelope)
      .map(aggOpt ⇒ ApplyEventsSuccess[Agg](aggOpt.asJava))
      .recover {
        case e ⇒
          ApplyEventsFailure[Agg](e)
      }
    FutureConverters.toJava(result)
  }
}
