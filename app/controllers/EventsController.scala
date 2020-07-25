package controllers

import ch.japanimpact.api.APIErrorsHelper
import ch.japanimpact.api.events.events.{Event, SimpleEvent, Visibility}
import ch.japanimpact.auth.api.apitokens.AuthorizationActions._
import ch.japanimpact.auth.api.apitokens.{App, AuthentifiedPrincipal, AuthorizationActions}
import javax.inject.Inject
import models.EventsModel
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.ExecutionContext
import scala.languageFeature.implicitConversions

class EventsController @Inject()(cc: ControllerComponents, events: EventsModel, authorize: AuthorizationActions)
                                (implicit ec: ExecutionContext) extends AbstractController(cc) with APIErrorsHelper {

  import events.AttributeFilter._


  def getEvent(id: Int) = authorize.optional.async { rq =>
    events.getEvent(id, attrFilterByPrincipal(rq.principal)).map {
      case Some(event) =>
        if (isVisible(event, rq.principal))
          Ok(Json.toJson(event))
        else APIError(Forbidden, "forbidden", "You cannot access this event")
      case None => APIError(NotFound, "not_found", "No such event found.")
    }
  }
  def getEvents(visibility: Option[String], isTest: Option[Boolean], isMainstream: Option[Boolean]) = authorize.optional.async { rq =>
    events.getEvents(visibility, isTest, isMainstream, Map(), attrFilterByPrincipal(rq.principal))
      .map(_.filter(ev => isVisible(ev, rq.principal)))
      .map(res => Ok(Json.toJson(res)))
  }


  def getCurrentEvent(visibility: Option[String], isTest: Option[Boolean], isMainstream: Option[Boolean]) = authorize.optional.async { rq =>
    events.getEvents(visibility, isTest, isMainstream, Map(), attrFilterByPrincipal(rq.principal))
      .map(_
        .filter(ev => isVisible(ev, rq.principal))
        .toList
        .sortBy(_.event.start)
        .headOption
      )
      .map {
        case Some(event) =>
          Ok(Json.toJson(event))
        case None => APIError(NotFound, "not_found", "No such event found.")
      }
  }

  def getCurrentPublicEvent(mainstream: Option[Boolean]) =
    getCurrentEvent(Some("Public"), Some(false), mainstream)

  def getCurrentMainstreamEvent =
    getCurrentPublicEvent(Some(true))

  /* CRUD */
  def createEvent = authorize("events/create").async(parse.json[SimpleEvent]) { rq =>
    events.createEvent(rq.body).map(ev => Ok(Json.toJson(ev)))
  }

  def updateEvent(event: Int) = authorize("events/update").async(parse.json[SimpleEvent]) { rq =>
    events.updateEvent(event, rq.body).map(ev => Ok)
  }

  def setAttribute(event: Int, key: String) = authorize(OnlyApps, "events/attributes/set").async(parse.text(1000)) { rq =>
    events.setAttribute(event, key, rq.body, rq.principal.principal.id, id => canModify(rq.principal, id, "set"))
      .map {
        case true => Ok
        case false => Forbidden
      }
  }

  def deleteAttribute(event: Int, key: String) = authorize(OnlyApps, "events/attributes/delete").async { rq =>
    events.deleteAttribute(event, key, id => canModify(rq.principal, id, "delete"))
      .map {
        case true => Ok
        case false => Forbidden
      }
  }



  private def attrFilterByPrincipal(ppal: Option[AuthentifiedPrincipal]): AttributeFilter = {
    ppal.map { ppal =>
      if (ppal.hasScope("events/attributes/full"))
        AllAttributes
      else if (ppal.hasScope("events/attribute/own") && ppal.principal.isInstanceOf[App])
        OnlyApp(ppal.principal.id)
      else OnlyPublic
    }.getOrElse(OnlyPublic)
  }

  private def isVisible(event: Event, principal: Option[AuthentifiedPrincipal]): Boolean = {
    if (event.event.visibility == Visibility.Public)
      if (event.event.isTest) principal.exists(_.hasScope("events/read/test"))
      else true
    else if (event.event.visibility == Visibility.Internal)
      principal.exists(_.hasScope("events/read/internal"))
    else
      principal.exists(_.hasScope("events/read/*"))
  }

  private implicit def visibilityFromString(src: Option[String]): Option[Visibility.Value] =
    src.flatMap(e => Visibility.values.find(_.toString == e))


  private def canModify(principal: AuthentifiedPrincipal, attributeOwner: Int, action: String) = {
    if (attributeOwner == principal.principal.id) true
    else principal.hasScope(s"events/attributes/$action/$attributeOwner")
  }
}
