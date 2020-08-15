package ch.japanimpact.api.events

import ch.japanimpact.api.APIError
import ch.japanimpact.api.events.events.{Event, SimpleEvent, Visibility}
import ch.japanimpact.auth.api.apitokens.{APITokensService, AppTokenRequest}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


/**
 * @author Louis Vialar
 */
@Singleton
class HttpEventsService @Inject()(ws: WSClient, config: Configuration, tokens: APITokensService, cache: AsyncCacheApi)(implicit ec: ExecutionContext) extends EventsService {
  private val apiBase = config.get[String]("events.baseUrl")
  private val cacheDuration = config.getOptional[Duration]("events.cacheDuration").getOrElse(10.minutes)
  private val token = new TokenHolder

  private def withToken[T](endpoint: String)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIError, T]] =
    token()
      .map(token => ws.url(s"$apiBase/$endpoint").addHttpHeaders("Authorization" -> ("Bearer " + token)))
      .flatMap(r => mapping(r)(exec)(map))
      .recover {
        case ex: Throwable => Left(APIError(ex.getClass.getName, ex.getMessage))
      }

  private def mapping[T](request: WSRequest)(exec: WSRequest => Future[WSResponse])(map: JsValue => T): Future[Either[APIError, T]] = {
    val r = exec(request)

    r.map { response =>

      if (response.status == 200) {
        try {
          Right(map(response.json))
        } catch {
          case e: Exception =>
            e.printStackTrace()
            println(response.body)
            Left(APIError("unknown_error", "Unknown error with success response mapping"))
        }
      } else {
        try {
          Left(response.json.as[APIError])
        } catch {
          case e: Exception =>
            e.printStackTrace()
            Left(APIError("unknown_error", "Unknown error with code " + response.status))
        }
      }

    }.recover {
      case ex: Throwable => Left(APIError(ex.getClass.getName, ex.getMessage))
    }
  }

  private def cacheOnlySuccess[T](cacheKey: String)(orElse: => Future[Either[APIError, T]]): Future[Either[APIError, T]] = {
    cache.getOrElseUpdate(cacheKey, cacheDuration)(orElse)
      .map { o =>
        if (o.isLeft) cache.remove(cacheKey)
        o
      }

  }

  override def getEvents(visibility: Option[Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, Iterable[Event]]] = {
    val params = List(visibility.map(v => "visibility" -> v.toString),
      isTest.map(t => "isTest" -> t.toString),
      isMainstream.map(m => "isMainstream" -> m.toString)).flatten

    val key = "events.get:" + params.mkString(";")

    cacheOnlySuccess(key) {
      withToken(s"/events")(_
        .withQueryStringParameters(params: _*)
        .get)(_.as[Iterable[Event]])
    }
  }

  override def getCurrentEvent(visibility: Option[Visibility.Value] = None, isTest: Option[Boolean] = None, isMainstream: Option[Boolean] = None): Future[Either[APIError, Event]] = {
    val params = List(visibility.map(v => "visibility" -> v.toString),
      isTest.map(t => "isTest" -> t.toString),
      isMainstream.map(m => "isMainstream" -> m.toString)).flatten


    val key = "events.current:" + params.mkString(";")

    cacheOnlySuccess(key) {
      withToken(s"/events/current")(_
        .withQueryStringParameters(params: _*)
        .get())(_.as[Event])
    }
  }

  @deprecated
  override def getCachedEvents: Future[Iterable[Event]] = this.getEvents().map(_.toOption.getOrElse(Iterable.empty))

  override def getEvent(id: Int): Future[Either[APIError, Event]] = {
    cacheOnlySuccess(s"event.$id") {
      withToken(s"/events/$id")(_.get)(_.as[Event])
    }
  }

  override def getCurrentPublicEvent: Future[Either[APIError, Event]] =
    cacheOnlySuccess("event.currentPublic") {
      withToken(s"/events/current/public")(_.get)(_.as[Event])
    }

  override def getCurrentMainstreamEvent: Future[Either[APIError, Event]] =
    cacheOnlySuccess("event.currentMainstream") {
      withToken(s"/events/current/mainstream")(_.get)(_.as[Event])
    }

  @deprecated
  override def getCachedMainstreamEvent: Future[Option[Event]] = this.getCurrentMainstreamEvent.map(_.toOption)

  override def createEvent(event: SimpleEvent): Future[Either[APIError, SimpleEvent]] =
    withToken("/events")(_.post(Json.toJson(event)))(_.as[SimpleEvent])

  override def updateEvent(id: Int, event: SimpleEvent): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id")(_.put(Json.toJson(event)))(_ => true)

  /**
   *
   * @param id
   * @param key
   * start with `_` to make it invisible to unlogged people
   * start with `__` to make it visible only to your app
   * @param value
   */
  override def setAttribute(id: Int, key: String, value: String): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id/$key")(_.put(value))(_ => true)

  override def deleteAttribute(id: Int, key: String): Future[Either[APIError, Boolean]] =
    withToken(s"/events/$id/$key")(_.delete)(_ => true)


  private class TokenHolder {
    var token: String = _
    var exp: Long = _

    def apply(): Future[String] = {
      if (token != null && exp > System.currentTimeMillis() + 1000) Future.successful(token)
      else {
        tokens.getToken(AppTokenRequest(Set("events/*"), Set("events"), 48.hours.toSeconds))
          .map {
            case Right(token) =>
              this.token = token.token
              this.exp = System.currentTimeMillis() + token.duration * 1000 - 1000

              this.token
            case _ => throw new Exception("No token returned")
          }
      }
    }
  }

}


