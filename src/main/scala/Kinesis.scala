package com.gu.socialCacheClearing

import com.amazonaws.services.kinesis.clientlibrary.types.UserRecord
import com.amazonaws.services.kinesis.model.Record

import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import scala.collection.JavaConverters._
import com.gu.thrift.serializer.ThriftDeserializer
import com.gu.crier.model.event.v1.Event

import scala.collection.mutable

trait Kinesis[Event,  CapiEvent] {
  def capiEvents(event:  Event): List[CapiEvent]
}

object Kinesis {
  object Production extends Kinesis[KinesisEvent, Event] {
    override def capiEvents(event: KinesisEvent): List[Event] = {
      val rawRecords: List[Record] = event.getRecords.asScala.map(_.getKinesis).toList
      val userRecords: mutable.Buffer[UserRecord] = UserRecord.deaggregate(rawRecords.asJava).asScala

      (for {
        record <- userRecords
        event <- ThriftDeserializer.deserialize(record.getData.array)(Event).toOption.toSeq
      } yield event).toList
    }
  }

}