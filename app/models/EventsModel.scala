package models

import java.sql.{Connection, PreparedStatement, Timestamp}

import anorm.Macro.ColumnNaming
import anorm.SqlParser._
import anorm._
import ch.japanimpact.api.events.events.{Event, SimpleEvent, Visibility}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime

import scala.collection.immutable
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EventsModel @Inject()(dbApi: play.api.db.DBApi)(implicit ec: ExecutionContext) {
  private val db = dbApi database "default"

  implicit val jodaTimeFix: ToStatement[DateTime] = (s: PreparedStatement, index: Int, v: DateTime) => s.setTimestamp(index, new Timestamp(v.getMillis))
  implicit val eventParamList: ToParameterList[SimpleEvent] = Macro.toParameters[SimpleEvent]()
  val eventColumnNaming: ColumnNaming = (s: String) => "event_" + ColumnNaming.SnakeCase(s)

  implicit val eventParser: RowParser[SimpleEvent] = Macro.namedParser[SimpleEvent]((p: String) => "events.event_" + ColumnNaming.SnakeCase(p))
  implicit val attributeParser: RowParser[EventAttribute] = Macro.namedParser[EventAttribute](ColumnNaming.SnakeCase)

  def createEvent(event: SimpleEvent): Future[SimpleEvent] = Future(db.withConnection { implicit c =>
    val id = SqlUtils.insertOne("events", event.copy(id = None), eventColumnNaming)
    event.copy(id = Some(id))
  })

  def updateEvent(id: Int, event: SimpleEvent): Future[Boolean] = Future(db.withConnection { implicit c =>
    SqlUtils.replaceOne("events", "id", event.copy(id = Some(id)), eventColumnNaming) > 0
  })

  private var nextHousekeep = 0L

  private def housekeep() = {
    if (nextHousekeep < System.currentTimeMillis()) {
      println("Running housekeeping...")
      Future(db.withConnection {
        implicit c => SQL"UPDATE events SET event_visibility = 'Archived' WHERE event_archive < CURRENT_TIMESTAMP".execute()
      })

      nextHousekeep = System.currentTimeMillis() + 24.hours.toMillis
    }
    ()
  }


  object AttributeFilter {
    type AttributeFilter = (EventAttribute => Boolean)

    val AllAttributes: AttributeFilter = _ => true
    val OnlyPublic: AttributeFilter = !_.isPrivate

    def OnlyApp(appId: Int): AttributeFilter = attr => (!attr.isAppPrivate || attr.eventAttributeOwner == appId)
  }

  import AttributeFilter._

  def eventParser(attributeFilter: AttributeFilter = AllAttributes): ResultSetParser[immutable.Iterable[Event]] = {
    ((eventParser ~ attributeParser.?)
      .map { case a ~ b => (a, b) }
      .*).map(lst => lst.groupMap(_._1)(_._2).map {
      case (event, attributes) =>
        val attrMap = attributes.filter(_.nonEmpty).map(_.get).filter(attributeFilter)
          .map(attr => attr.eventAttributeKey -> attr.eventAttributeValue)
          .toMap

        Event(event, attrMap)
    })
  }

  def getEvent(event: Int, filter: AttributeFilter) = {
    housekeep();
    Future(db.withConnection { implicit c =>
      SQL"SELECT * FROM events LEFT JOIN event_attributes ea on events.event_id = ea.event_id WHERE events.event_id = $event"
        .as(eventParser(filter))
        .headOption
    })
  }

  def getEvents(visibility: Option[Visibility.Value], isTest: Option[Boolean], isMainstream: Option[Boolean], attributes: Map[String, String], filter: AttributeFilter) = Future(db.withConnection { implicit c =>
    housekeep();

    var whereClauses = List[String]()
    var params = List[NamedParameter]()

    if (visibility.isDefined) {
      whereClauses = "event_visibility = {visibility}" :: whereClauses
      params = ("visibility" -> visibility.get) :: params
    }

    if (isTest.isDefined) {
      whereClauses = "event_is_test = {isTest}" :: whereClauses
      params = ("isTest" -> isTest.get) :: params
    }

    if (isMainstream.isDefined) {
      whereClauses = "event_is_mainstream = {isMainstream}" :: whereClauses
      params = ("isMainstream" -> isMainstream.get) :: params
    }

    val where = if (whereClauses.nonEmpty) "WHERE " + whereClauses.mkString(" AND ") else ""

    SQL("SELECT * FROM events LEFT JOIN event_attributes ea on events.event_id = ea.event_id " + where)
      .on(params: _*)
      .as(eventParser(filter))
      .filter(ev => {
        attributes.forall {
          case (key, value) => ev.attributes.get(key).contains(value)
        }
      })
  })

  private def getAttributeOwner(event: Int, attribute: String)(implicit c: Connection): Option[Int] =
    SQL"SELECT event_attribute_owner FROM event_attributes WHERE event_id = $event AND event_attribute_key = $attribute"
      .as(scalar[Int].singleOpt)

  def setAttribute(event: Int, key: String, value: String, app: Int, isAllowed: Int => Boolean): Future[Boolean] = Future(db.withConnection { implicit c =>
    val (allowed, exists) = getAttributeOwner(event, key) match {
      case Some(id) => (isAllowed(id), true)
      case None => (true, false)
    }

    if (allowed) {
      val rq = if (exists)
        SQL"UPDATE event_attributes SET event_attribute_value = $value WHERE event_id = $event AND event_attribute_key = $key"
      else
        SQL"INSERT INTO event_attributes(event_id, event_attribute_owner, event_attribute_key, event_attribute_value) VALUES ($event, $app, $key, $value)"

      rq.execute()
      true
    } else false
  })

  def deleteAttribute(event: Int, key: String, isAllowed: Int => Boolean): Future[Boolean] = Future(db.withConnection { implicit c =>
    val allowed = getAttributeOwner(event, key) match {
      case Some(id) => isAllowed(id)
      case None => true
    }

    if (allowed) {
      SQL"DELETE FROM event_attributes WHERE event_id = $event AND event_attribute_key = $key"
        .execute()
      true
    } else false
  })

}
