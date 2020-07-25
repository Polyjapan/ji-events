package ch.japanimpact.api.events

import org.joda.time.DateTime
import play.api.libs.json._

package object events {
  /**
   * Represents an event without its attributes
   *
   * @param id           the internal id of the event, used to refer to it
   * @param name         the public displayable name of the event
   * @param location     the real life displayable location of the event
   * @param visibility   the visibility state of the event
   * @param start        the start datetime of the event
   * @param end          the end datetime of the event
   * @param archive      the datetime at which the event will automatically be considered archived
   * @param isTest       if true, this event is only a test and should not be used for the public
   * @param isMainstream if false, this event is not a mainstream event (not JI)
   */
  case class SimpleEvent(id: Option[Int], name: String, location: String, visibility: Visibility.Value, start: DateTime,
                         end: DateTime, archive: DateTime, isTest: Boolean, isMainstream: Boolean)

  /**
   * Represents an event with its attributes
   *
   * @param event      the event
   * @param attributes the attributes of the event (a map key -> value)
   */
  case class Event(event: SimpleEvent, attributes: Map[String, String])

  /**
   * The visibility of an event. Each app uses it as they wish - it's just a reference.
   */
  object Visibility extends Enumeration {
    type Visibility = Value
    /**
     * The event is a draft, this means it's not yet ready to be used - either internally or publically
     */
    val Draft: Visibility = Value
    /**
     * The event is usable internally (i.e. on staff spaces and all) but not publically (should not be displayed in shop
     * for example)
     */
    val Internal: Visibility = Value
    /**
     * The event is public - all apps can access it
     */
    val Public: Visibility = Value
    /**
     * The event is archived. It's like Internal but in an unmodifiable fashion. You should not change archived events.
     */
    val Archived: Visibility = Value
  }

  implicit val ReplacementPolicyFormat: Format[Visibility.Value] = Json.formatEnum(Visibility)
  implicit val datetimeRead: Reads[DateTime] = JodaReads.DefaultJodaDateTimeReads
  implicit val datetimeWrite: Writes[DateTime] = JodaWrites.JodaDateTimeWrites
  implicit val SimpleEventFormat: Format[SimpleEvent] = Json.format[SimpleEvent]
  implicit val EventFormat: Format[Event] = Json.format[Event]
}
